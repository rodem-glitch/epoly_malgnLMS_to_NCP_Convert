package kr.go.growailms.contentsummary.service;

import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 왜: Kollus webhook 요청은 빠르게 응답해야 하고, 전사/요약은 오래 걸립니다.
 * 그래서 webhook에서는 "DB에 작업을 쌓기(PENDING)"만 하고, 실제 처리는 이 워커가 백그라운드로 수행합니다.
 *
 * 처리 흐름(상태):
 * - PENDING/FAILED/PROCESSING(타임아웃) -> 전사 처리 -> TRANSCRIBED
 * - TRANSCRIBED/SUMMARY_FAILED/SUMMARY_PROCESSING(타임아웃) -> 요약 처리 -> DONE
 */
@Component
public class ContentTranscriptionWorker {

    private final ContentSummaryWorkerProperties workerProperties;
    private final JdbcTemplate jdbcTemplate;
    private final ContentSummaryService contentSummaryService;

    public ContentTranscriptionWorker(
        ContentSummaryWorkerProperties workerProperties,
        JdbcTemplate jdbcTemplate,
        ContentSummaryService contentSummaryService
    ) {
        this.workerProperties = Objects.requireNonNull(workerProperties);
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
        this.contentSummaryService = Objects.requireNonNull(contentSummaryService);
    }

    @Scheduled(fixedDelayString = "${contentsummary.worker.poll-delay-ms:30000}")
    public void poll() {
        if (!workerProperties.enabled()) return;
        pollTranscriptionJobs();
        pollSummaryJobs();
    }

    private void pollTranscriptionJobs() {
        List<Long> candidates = jdbcTemplate.queryForList("""
            SELECT id
            FROM TB_KOLLUS_TRANSCRIPT
            WHERE status = 'PENDING'
               OR (status = 'FAILED' AND retry_count < ? AND TIMESTAMPDIFF(SECOND, updated_at, NOW()) >= ?)
               OR (status = 'PROCESSING' AND retry_count < ? AND TIMESTAMPDIFF(SECOND, updated_at, NOW()) >= ?)
            ORDER BY id ASC
            LIMIT ?
            """,
            Long.class,
            workerProperties.maxRetries(),
            workerProperties.retryDelaySeconds(),
            workerProperties.maxRetries(),
            workerProperties.processingTimeoutSeconds(),
            workerProperties.batchSize()
        );

        for (Long id : candidates) {
            if (id == null) continue;
            if (!claimTranscription(id)) continue;
            contentSummaryService.processTranscriptionById(id);
        }
    }

    private void pollSummaryJobs() {
        List<Long> candidates = jdbcTemplate.queryForList("""
            SELECT t.id
            FROM TB_KOLLUS_TRANSCRIPT t
            LEFT JOIN TB_RECO_CONTENT r
              ON r.lesson_id = t.media_content_key
            WHERE t.status = 'TRANSCRIBED'
               OR (t.status = 'DONE' AND r.id IS NULL)
               OR (t.status = 'SUMMARY_FAILED' AND t.retry_count < ? AND TIMESTAMPDIFF(SECOND, t.updated_at, NOW()) >= ?)
               OR (t.status = 'SUMMARY_PROCESSING' AND t.retry_count < ? AND TIMESTAMPDIFF(SECOND, t.updated_at, NOW()) >= ?)
            ORDER BY t.id ASC
            LIMIT ?
            """,
            Long.class,
            workerProperties.maxRetries(),
            workerProperties.retryDelaySeconds(),
            workerProperties.maxRetries(),
            workerProperties.processingTimeoutSeconds(),
            workerProperties.batchSize()
        );

        for (Long id : candidates) {
            if (id == null) continue;
            if (!claimSummary(id)) continue;
            contentSummaryService.processSummaryById(id);
        }
    }

    private boolean claimTranscription(Long id) {
        // 왜: 서버가 여러 대여도 같은 id를 중복 처리하지 않도록, DB에서 먼저 "점유(PROCESSING)"합니다.
        int updated = jdbcTemplate.update("""
            UPDATE TB_KOLLUS_TRANSCRIPT
               SET status = 'PROCESSING',
                   retry_count = CASE WHEN status = 'PROCESSING' THEN retry_count + 1 ELSE retry_count END,
                   last_error = CASE WHEN status = 'PROCESSING' THEN '이전 전사 처리(PROCESSING)가 오래 지속되어 재시도합니다.' ELSE last_error END,
                   updated_at = NOW()
             WHERE id = ?
               AND (
                 status = 'PENDING'
                 OR (status = 'FAILED' AND retry_count < ? AND TIMESTAMPDIFF(SECOND, updated_at, NOW()) >= ?)
                 OR (status = 'PROCESSING' AND retry_count < ? AND TIMESTAMPDIFF(SECOND, updated_at, NOW()) >= ?)
               )
            """,
            id,
            workerProperties.maxRetries(),
            workerProperties.retryDelaySeconds(),
            workerProperties.maxRetries(),
            workerProperties.processingTimeoutSeconds()
        );
        return updated == 1;
    }

    private boolean claimSummary(Long id) {
        // 왜: 요약도 중복 처리되면 LLM 비용이 늘어나므로, 별도 상태로 점유(SUMMARY_PROCESSING)합니다.
        int updated = jdbcTemplate.update("""
            UPDATE TB_KOLLUS_TRANSCRIPT
               SET status = 'SUMMARY_PROCESSING',
                   retry_count = CASE WHEN status = 'SUMMARY_PROCESSING' THEN retry_count + 1 ELSE retry_count END,
                   last_error = CASE WHEN status = 'SUMMARY_PROCESSING' THEN '이전 요약 처리(SUMMARY_PROCESSING)가 오래 지속되어 재시도합니다.' ELSE last_error END,
                   updated_at = NOW()
             WHERE id = ?
               AND (
                 status = 'TRANSCRIBED'
                 OR status = 'DONE'
                 OR (status = 'SUMMARY_FAILED' AND retry_count < ? AND TIMESTAMPDIFF(SECOND, updated_at, NOW()) >= ?)
                 OR (status = 'SUMMARY_PROCESSING' AND retry_count < ? AND TIMESTAMPDIFF(SECOND, updated_at, NOW()) >= ?)
               )
            """,
            id,
            workerProperties.maxRetries(),
            workerProperties.retryDelaySeconds(),
            workerProperties.maxRetries(),
            workerProperties.processingTimeoutSeconds()
        );
        return updated == 1;
    }
}
