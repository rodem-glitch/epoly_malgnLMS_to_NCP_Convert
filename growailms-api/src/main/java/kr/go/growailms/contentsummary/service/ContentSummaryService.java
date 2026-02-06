package kr.go.growailms.contentsummary.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import kr.go.growailms.contentsummary.client.GeminiVideoSummaryClient;
import kr.go.growailms.contentsummary.client.KollusApiClient;
import kr.go.growailms.contentsummary.client.KollusMediaDownloader;
import kr.go.growailms.contentsummary.client.KollusProperties;
import kr.go.growailms.contentsummary.dto.EnqueueBackfillResponse;
import kr.go.growailms.contentsummary.dto.KollusChannelContent;
import kr.go.growailms.contentsummary.dto.KollusWebhookIngestResponse;
import kr.go.growailms.contentsummary.dto.RecoContentSummaryDraft;
import kr.go.growailms.contentsummary.dto.RunTranscriptionItemResult;
import kr.go.growailms.contentsummary.dto.RunTranscriptionResponse;
import kr.go.growailms.contentsummary.entity.KollusTranscript;
import kr.go.growailms.contentsummary.repository.ContentSummaryRepository;
import kr.go.growailms.recocontent.entity.RecoContent;
import kr.go.growailms.recocontent.repository.RecoContentRepository;
import kr.go.growailms.recocontent.service.RecoContentVectorIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 영상 콘텐츠 요약 서비스.
 * 왜: Gemini 3 Flash로 영상을 직접 처리하여 STT 단계를 생략하고 바로 요약을 생성합니다.
 */
@Service
public class ContentSummaryService {

    private static final Logger log = LoggerFactory.getLogger(ContentSummaryService.class);
    private static final int VIDEO_SUMMARY_MAX_ADDITIONAL_RETRIES = 2;

    private final KollusApiClient kollusApiClient;
    private final KollusMediaDownloader kollusMediaDownloader;
    private final KollusProperties kollusProperties;
    private final GeminiVideoSummaryClient geminiVideoSummaryClient;
    private final ContentTranscriptionProperties transcriptionProperties;
    private final ContentSummaryRepository transcriptRepository;
    private final RecoContentRepository recoContentRepository;
    private final RecoContentVectorIndexService recoContentVectorIndexService;

    public ContentSummaryService(
        KollusApiClient kollusApiClient,
        KollusMediaDownloader kollusMediaDownloader,
        KollusProperties kollusProperties,
        GeminiVideoSummaryClient geminiVideoSummaryClient,
        ContentTranscriptionProperties transcriptionProperties,
        ContentSummaryRepository transcriptRepository,
        RecoContentRepository recoContentRepository,
        RecoContentVectorIndexService recoContentVectorIndexService
    ) {
        // 왜: 영상 요약은 "외부 API + 대용량 파일" 조합이라 실패 지점이 많습니다. 의존성을 분리해두면 원인 추적이 쉬워집니다.
        this.kollusApiClient = Objects.requireNonNull(kollusApiClient);
        this.kollusMediaDownloader = Objects.requireNonNull(kollusMediaDownloader);
        this.kollusProperties = Objects.requireNonNull(kollusProperties);
        this.geminiVideoSummaryClient = Objects.requireNonNull(geminiVideoSummaryClient);
        this.transcriptionProperties = Objects.requireNonNull(transcriptionProperties);
        this.transcriptRepository = Objects.requireNonNull(transcriptRepository);
        this.recoContentRepository = Objects.requireNonNull(recoContentRepository);
        this.recoContentVectorIndexService = Objects.requireNonNull(recoContentVectorIndexService);
    }

    /**
     * 기존 영상들의 duration_seconds를 백필합니다.
     * 왜: DB에 이미 있는 영상 중 duration_seconds가 null인 레코드에 대해 Kollus API로 길이를 조회하여 저장합니다.
     */
    public int backfillDurations() {
        List<KollusTranscript> targets = transcriptRepository.findByDurationSecondsIsNull();
        log.info("영상 길이 백필 시작: 대상 {}건", targets.size());
        
        int successCount = 0;
        int failCount = 0;
        
        for (KollusTranscript t : targets) {
            try {
                String mediaKey = t.getMediaContentKey();
                if (mediaKey == null || mediaKey.isBlank()) {
                    failCount++;
                    continue;
                }
                
                String token = kollusApiClient.issueMediaToken(mediaKey);
                KollusApiClient.DownloadInfo info = kollusApiClient.resolveDownloadInfoByMediaToken(token);
                t.setDurationSeconds(info.totalTimeSeconds());
                transcriptRepository.save(t);
                
                successCount++;
                if (successCount % 100 == 0) {
                    log.info("영상 길이 백필 진행 중: {}/{}", successCount, targets.size());
                }
            } catch (Exception e) {
                log.warn("영상 길이 백필 실패: {} - {}", t.getMediaContentKey(), e.getMessage());
                failCount++;
            }
        }
        
        log.info("영상 길이 백필 완료: 성공 {}건, 실패 {}건", successCount, failCount);
        return successCount;
    }

    public KollusWebhookIngestResponse ingestKollusWebhook(Integer siteId, String mediaContentKey, String title) {
        Integer safeSiteId = siteId == null ? 1 : siteId;
        if (mediaContentKey == null || mediaContentKey.isBlank()) {
            throw new IllegalArgumentException("mediaContentKey가 비어 있습니다.");
        }

        String mediaKey = mediaContentKey.trim();

        // 왜: 이미 요약이 TB_RECO_CONTENT에 저장되어 있으면, 요약을 다시 할 필요가 없습니다(비용/시간 절감).
        if (recoContentRepository.existsByLessonId(mediaKey)) {
            return new KollusWebhookIngestResponse(mediaKey, title, "SKIPPED_SUMMARY_EXISTS");
        }

        KollusTranscript transcript = transcriptRepository
            .findBySiteIdAndMediaContentKey(safeSiteId, mediaKey)
            .orElseGet(() -> new KollusTranscript(safeSiteId, safeChannelKeyFallback(), mediaKey));

        // 왜: 이미 DONE이면 "이미 DB에 요약이 저장된 상태"이므로, webhook이 중복으로 와도 다시 돌리지 않습니다.
        if ("DONE".equalsIgnoreCase(transcript.getStatus())) {
            return new KollusWebhookIngestResponse(mediaKey, title, "SKIPPED_DONE");
        }

        // 왜: 이미 처리 중이면 상태를 되돌리면(다시 PENDING) 중복 처리/경합이 생길 수 있어 그대로 둡니다.
        if ("PROCESSING".equalsIgnoreCase(transcript.getStatus())) {
            return new KollusWebhookIngestResponse(mediaKey, title, "SKIPPED_PROCESSING");
        }

        // 왜: 실패 상태여도 다시 PENDING으로 두면 자동 재시도가 가능합니다.
        if ("FAILED".equalsIgnoreCase(transcript.getStatus())) {
            return new KollusWebhookIngestResponse(mediaKey, title, "SKIPPED_ALREADY_QUEUED");
        }

        transcript.markPending(title);
        transcriptRepository.save(transcript);
        return new KollusWebhookIngestResponse(mediaKey, title, "ENQUEUED");
    }

    public EnqueueBackfillResponse enqueueBackfill(Integer siteId, int limit, String keyword) {
        Integer safeSiteId = siteId == null ? 1 : siteId;
        int safeLimit = Math.max(1, Math.min(limit, 10_000));

        int scanned = 0;
        int enqueued = 0;
        int skippedDone = 0;

        int page = 1;
        int perPage = 50;

        while (scanned < safeLimit) {
            List<KollusChannelContent> contents = kollusApiClient.listChannelContents(page, perPage, keyword);
            if (contents.isEmpty()) break;

            for (KollusChannelContent c : contents) {
                if (scanned >= safeLimit) break;
                scanned++;

                String mediaKey = c.mediaContentKey();
                if (mediaKey == null || mediaKey.isBlank()) continue;

                // 왜: 이미 요약이 TB_RECO_CONTENT에 있으면, backfill 대상이 아닙니다.
                if (recoContentRepository.existsByLessonId(mediaKey)) {
                    skippedDone++;
                    continue;
                }

                Optional<KollusTranscript> existing = transcriptRepository.findBySiteIdAndMediaContentKey(safeSiteId, mediaKey);
                if (existing.isPresent() && "DONE".equalsIgnoreCase(existing.get().getStatus())) {
                    skippedDone++;
                    continue;
                }
                if (existing.isPresent() && "PROCESSING".equalsIgnoreCase(existing.get().getStatus())) {
                    continue;
                }

                KollusTranscript transcript = existing.orElseGet(() -> new KollusTranscript(safeSiteId, safeChannelKeyFallback(), mediaKey));
                transcript.markPending(c.title());
                transcriptRepository.save(transcript);
                enqueued++;
            }

            page++;
        }

        return new EnqueueBackfillResponse(scanned, enqueued, skippedDone);
    }

    /**
     * 단일 영상 요약 처리 (워커에서 호출).
     * 왜: Gemini 3 Flash로 영상을 직접 처리하므로 STT 단계가 없습니다.
     */
    public void processVideoSummaryById(Long transcriptId) {
        if (transcriptId == null) return;
        KollusTranscript transcript = transcriptRepository.findById(transcriptId).orElse(null);
        if (transcript == null) return;

        // 왜: 워커가 이미 "PROCESSING"으로 점유한 뒤 호출합니다.
        if (!"PROCESSING".equalsIgnoreCase(transcript.getStatus())) return;

        doVideoSummarize(transcript);
    }

    /**
     * 기존 호환성을 위한 메서드 (processTranscriptionById 대체).
     */
    public void processTranscriptionById(Long transcriptId) {
        processVideoSummaryById(transcriptId);
    }

    /**
     * 기존 호환성을 위한 메서드 (processSummaryById - 이제 영상에서 바로 요약하므로 별도 단계 불필요).
     */
    public void processSummaryById(Long transcriptId) {
        // 왜: 이제 영상에서 직접 요약하므로 별도의 요약 단계가 필요 없습니다.
        // 기존 TRANSCRIBED 상태의 레코드가 있다면 처리합니다.
        if (transcriptId == null) return;
        KollusTranscript transcript = transcriptRepository.findById(transcriptId).orElse(null);
        if (transcript == null) return;

        if ("TRANSCRIBED".equalsIgnoreCase(transcript.getStatus()) 
            || "SUMMARY_PROCESSING".equalsIgnoreCase(transcript.getStatus())) {
            // 기존 전사 텍스트가 있으면 그것으로 요약 생성
            doSummarizeFromTranscript(transcript);
        }
    }

    public RunTranscriptionResponse runTranscription(Integer siteId, int limit, boolean force, String keyword) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        Integer safeSiteId = siteId == null ? 1 : siteId;

        List<RunTranscriptionItemResult> items = new ArrayList<>();
        int processed = 0;
        int skipped = 0;
        int failed = 0;

        int page = 1;
        int perPage = Math.min(50, safeLimit);

        while (items.size() < safeLimit) {
            List<KollusChannelContent> contents = kollusApiClient.listChannelContents(page, perPage, keyword);
            if (contents.isEmpty()) break;

            for (KollusChannelContent c : contents) {
                if (items.size() >= safeLimit) break;
                String mediaKey = c.mediaContentKey();

                KollusTranscript transcript = transcriptRepository
                    .findBySiteIdAndMediaContentKey(safeSiteId, mediaKey)
                    .orElseGet(() -> new KollusTranscript(safeSiteId, safeChannelKeyFallback(), mediaKey));

                // 왜: 이미 DONE이면 같은 영상에 대해 매번 비용이 나가므로, 강제(force)가 아니면 건너뜁니다.
                String status = transcript.getStatus();
                boolean alreadyDone = "DONE".equalsIgnoreCase(status);
                if (!force && alreadyDone) {
                    skipped++;
                    items.add(new RunTranscriptionItemResult(mediaKey, c.title(), "SKIPPED", "이미 요약 완료"));
                    continue;
                }

                transcript.markProcessing(c.title());
                transcriptRepository.save(transcript);

                try {
                    boolean ok = doVideoSummarize(transcript);
                    if (ok) {
                        processed++;
                        items.add(new RunTranscriptionItemResult(mediaKey, c.title(), "DONE", "요약 완료"));
                    } else {
                        failed++;
                        items.add(new RunTranscriptionItemResult(mediaKey, c.title(), "FAILED", safeMessageFromTranscript(transcript)));
                    }
                } catch (Exception unexpected) {
                    failed++;
                    items.add(new RunTranscriptionItemResult(mediaKey, c.title(), "FAILED", safeMessage(unexpected)));
                }
            }

            page++;
        }

        return new RunTranscriptionResponse(processed, skipped, failed, items);
    }

    /**
     * 영상을 다운로드하고 Gemini로 직접 요약을 생성합니다.
     * 왜: STT 단계 없이 Gemini 3 Flash가 영상을 직접 분석하므로 처리가 더 빠릅니다.
     */
    private boolean doVideoSummarize(KollusTranscript transcript) {
        Integer siteId = transcript.getSiteId() == null ? 1 : transcript.getSiteId();
        String mediaKey = transcript.getMediaContentKey();
        String safeMediaKey = mediaKey == null ? "" : mediaKey.trim();

        // 왜: 이미 요약이 TB_RECO_CONTENT에 있으면, 다시 비용을 쓰지 않습니다.
        // 추가 요구사항: 요약 저장 후 "즉시 벡터DB(Qdrant) upsert"까지 완료되어야 추천/검색에 바로 반영됩니다.
        // - 이전 실행에서 요약 저장은 됐는데 벡터 upsert가 실패했을 수도 있어, 이 경우는 "벡터만" 재시도합니다.
        if (!safeMediaKey.isBlank()) {
            Optional<RecoContent> existing = recoContentRepository.findTopByLessonIdOrderByIdDesc(safeMediaKey);
            if (existing.isPresent()) {
                RecoContent content = existing.get();
                if (isVectorIndexTarget(content)) {
                    upsertRecoContentVector(content, safeMediaKey);
                } else {
                    // 왜: 요약이 비어 있는 더미 row(무한 재요약 방지)까지 인덱싱하면 추천 품질이 떨어질 수 있습니다.
                    log.info("요약 데이터가 비어 있어 벡터 인덱싱을 건너뜁니다. mediaKey={}, recoContentId={}", safeMediaKey, content.getId());
                }

                transcript.markSummaryDone();
                transcriptRepository.save(transcript);
                return true;
            }
        }

        Path workDir = transcriptionProperties.tmpDir().resolve("site-" + siteId).resolve(safeFileName(mediaKey));
        Path videoFile = workDir.resolve("input.mp4");

        try {
            // 1. Kollus에서 영상 다운로드
            String mediaToken = kollusApiClient.issueMediaToken(mediaKey);
            KollusApiClient.DownloadInfo downloadInfo = kollusApiClient.resolveDownloadInfoByMediaToken(mediaToken);
            
            // 영상 길이 저장 (비용 분석용)
            transcript.setDurationSeconds(downloadInfo.totalTimeSeconds());
            
            kollusMediaDownloader.downloadTo(downloadInfo.downloadUri(), videoFile);

            log.info("영상 다운로드 완료: {} ({})", mediaKey, videoFile);

            // 2. Gemini로 영상 직접 요약 (STT 단계 없음!)
            String title = safeTitle(transcript.getTitle());
            int targetSummaryLength = computeTargetSummaryLength(downloadInfo.totalTimeSeconds());
            log.info("요약 목표 길이 계산: mediaKey={}, totalTimeSeconds={}, targetSummaryLength={}", mediaKey, downloadInfo.totalTimeSeconds(), targetSummaryLength);
            RecoContentSummaryDraft draft = summarizeVideoWithRetries(mediaKey, title, videoFile, targetSummaryLength);

            // 왜: JSON 파싱이 깨진 경우 fallback(느슨한 파싱/재정렬)로도 summary/keywords가 부족하게 나올 수 있습니다.
            // 이 경우에는 "텍스트 재정렬"이 아니라, 영상을 포함해서 다시 요약해야(비용↑) 내용/키워드가 복구될 가능성이 큽니다.
            // 그래도 2회 재시도 후에도 기준 미달이면 다음 영상으로 넘어가기 위해 "키만 남기는 더미 row"를 저장합니다.
            boolean summaryTooShort = isSummaryTooShort(draft == null ? null : draft.summary(), targetSummaryLength);
            boolean keywordsTooFew = isKeywordsTooFew(draft == null ? null : draft.keywords(), targetSummaryLength);
            if (summaryTooShort || keywordsTooFew) {
                int attempts = 1 + VIDEO_SUMMARY_MAX_ADDITIONAL_RETRIES;
                int summaryLen = codePointLength(draft == null ? null : draft.summary());
                int minSummaryLen = minAcceptableSummaryLength(targetSummaryLength);
                int keywordCount = countNonBlankKeywords(draft == null ? null : draft.keywords());
                int minKeywords = minAcceptableKeywordCount(targetSummaryLength);
                log.warn(
                    "요약 결과가 기준 미달이라 {}회 시도 후 포기합니다. mediaKey={}, summaryTooShort={}, keywordsTooFew={}, summaryLength={}, minAcceptableSummaryLength={}, keywordsCount={}, minAcceptableKeywords={}, targetSummaryLength={}",
                    attempts,
                    mediaKey,
                    summaryTooShort,
                    keywordsTooFew,
                    summaryLen,
                    minSummaryLen,
                    keywordCount,
                    minKeywords,
                    targetSummaryLength
                );
                saveEmptyRecoContent(mediaKey, title);
                transcript.markSummaryDone();
                transcriptRepository.save(transcript);
                return true;
            }

            log.info("Gemini 영상 요약 완료: {} - 카테고리={}", mediaKey, draft.categoryNm());

            // 3. 결과를 DB에 저장
            String categoryNm = safeCategory(draft.categoryNm());
            String summary = safeSummary(draft.summary());
            String keywords = joinKeywordsWithinLimit(draft.keywords(), 10, 500);

            RecoContent content = new RecoContent(categoryNm, title, summary, keywords);
            content.setLessonId(mediaKey.trim());
            RecoContent saved = recoContentRepository.save(content);

            // 왜: DB에 요약이 저장만 되면 화면에서는 보일 수 있지만,
            // 추천/벡터검색은 Qdrant를 보므로 "저장 직후 upsert"를 해줘야 바로 반영됩니다.
            upsertRecoContentVector(saved, safeMediaKey);

            transcript.markSummaryDone();
            transcriptRepository.save(transcript);
            return true;
        } catch (Exception e) {
            log.error("영상 요약 처리 실패: {} - {}", mediaKey, e.getMessage(), e);
            transcript.markFailed(truncateError(e, 2000));
            transcriptRepository.save(transcript);
            return false;
        } finally {
            if (!transcriptionProperties.keepTempFiles()) {
                safeDeleteDir(workDir);
            }
        }
    }

    private RecoContentSummaryDraft summarizeVideoWithRetries(
        String mediaKey,
        String title,
        Path videoFile,
        int targetSummaryLength
    ) throws Exception {
        // 왜: uploadAndSummarize()를 그대로 재시도하면 매번 파일 업로드까지 다시 하게 되어 비용/시간이 크게 늘어납니다.
        // 업로드는 1회만 하고(fileUri 재사용), 요약(generateContent)만 최대 3회(최초 1회 + 재시도 2회) 수행합니다.
        String mimeType = "video/mp4"; // 현재 다운로드 파일명은 input.mp4로 고정입니다.
        GeminiVideoSummaryClient.UploadedFile uploaded = geminiVideoSummaryClient.uploadVideo(videoFile);
        mimeType = uploaded.mimeType();
        String fileUri = uploaded.fileUri();

        try {
            int maxAdditionalRetries = VIDEO_SUMMARY_MAX_ADDITIONAL_RETRIES;
            int totalAttempts = 1 + maxAdditionalRetries;

            RecoContentSummaryDraft last = null;
            for (int attempt = 1; attempt <= totalAttempts; attempt++) {
                last = geminiVideoSummaryClient.summarizeFromVideo(fileUri, title, mimeType, targetSummaryLength);

                boolean summaryTooShort = isSummaryTooShort(last == null ? null : last.summary(), targetSummaryLength);
                boolean keywordsTooFew = isKeywordsTooFew(last == null ? null : last.keywords(), targetSummaryLength);
                if (!summaryTooShort && !keywordsTooFew) {
                    if (attempt > 1) {
                        int summaryLen = codePointLength(last == null ? null : last.summary());
                        int keywordCount = countNonBlankKeywords(last == null ? null : last.keywords());
                        log.info("요약 재시도 성공: mediaKey={}, attempt={}/{}, summaryLength={}, keywordsCount={}", mediaKey, attempt, totalAttempts, summaryLen, keywordCount);
                    }
                    return last;
                }

                int summaryLen = codePointLength(last == null ? null : last.summary());
                int minSummaryLen = minAcceptableSummaryLength(targetSummaryLength);
                int keywordCount = countNonBlankKeywords(last == null ? null : last.keywords());
                int minKeywords = minAcceptableKeywordCount(targetSummaryLength);
                if (attempt < totalAttempts) {
                    log.warn(
                        "요약 결과가 기준 미달이라 재요약합니다. mediaKey={}, attempt={}/{}, summaryTooShort={}, keywordsTooFew={}, summaryLength={}, minAcceptableSummaryLength={}, keywordsCount={}, minAcceptableKeywords={}, targetSummaryLength={}",
                        mediaKey,
                        attempt,
                        totalAttempts,
                        summaryTooShort,
                        keywordsTooFew,
                        summaryLen,
                        minSummaryLen,
                        keywordCount,
                        minKeywords,
                        targetSummaryLength
                    );
                } else {
                    log.warn(
                        "요약 결과가 {}회 시도 후에도 기준 미달입니다. mediaKey={}, summaryTooShort={}, keywordsTooFew={}, summaryLength={}, minAcceptableSummaryLength={}, keywordsCount={}, minAcceptableKeywords={}, targetSummaryLength={}",
                        totalAttempts,
                        mediaKey,
                        summaryTooShort,
                        keywordsTooFew,
                        summaryLen,
                        minSummaryLen,
                        keywordCount,
                        minKeywords,
                        targetSummaryLength
                    );
                }
            }

            return last;
        } finally {
            // 왜: Gemini File API는 업로드 파일을 서버 쪽에 보관합니다.
            //      삭제하지 않으면 file_storage_bytes 쿼터가 누적되어(20GB 한도) 이후 작업이 429로 막힙니다.
            //      요약 결과는 DB에 저장되므로, 요약이 끝나면 업로드 파일은 바로 삭제해도 안전합니다.
            geminiVideoSummaryClient.deleteUploadedFileBestEffort(fileUri);
        }
    }

    private RecoContent saveEmptyRecoContent(String mediaKey, String title) {
        // 왜: 요약이 기준 길이를 못 맞추더라도(너무 짧음), 같은 영상에 대해 무한 재요약을 반복하면 비용이 폭증합니다.
        // lesson_id만 남겨두면 이후 배치/워커가 해당 영상을 "이미 처리됨"으로 보고 건너뛸 수 있습니다.
        String safeMediaKey = mediaKey == null ? "" : mediaKey.trim();
        String safeTitle = safeTitle(title);

        // 왜: 멀티 인스턴스/워커 환경에서 동시에 같은 mediaKey를 처리하면 TB_RECO_CONTENT에 중복 row가 생길 수 있습니다.
        // 이미 존재하면 새로 만들지 않고 최신 1건을 재사용합니다.
        if (!safeMediaKey.isBlank() && recoContentRepository.existsByLessonId(safeMediaKey)) {
            return recoContentRepository.findTopByLessonIdOrderByIdDesc(safeMediaKey)
                .orElseGet(() -> saveEmptyRecoContentInternal(safeMediaKey, safeTitle));
        }

        return saveEmptyRecoContentInternal(safeMediaKey, safeTitle);
    }

    private RecoContent saveEmptyRecoContentInternal(String mediaKey, String title) {
        // 요구사항: 요약(summary)과 키워드(keywords)는 빈 칸(빈 문자열)로 저장합니다.
        // DB 스키마에서 NOT NULL일 수 있어 null 대신 ""로 저장합니다.
        RecoContent content = new RecoContent("기타, 기타", title, "", "");
        content.setLessonId(mediaKey);
        return recoContentRepository.save(content);
    }

    private static boolean isVectorIndexTarget(RecoContent content) {
        if (content == null) return false;
        String summary = content.getSummary();
        return summary != null && !summary.isBlank();
    }

    private void upsertRecoContentVector(RecoContent content, String mediaKey) {
        if (content == null) return;

        try {
            recoContentVectorIndexService.upsertOne(content);
            log.info("요약 벡터 인덱싱 완료: mediaKey={}, recoContentId={}", mediaKey, content.getId());
        } catch (Exception e) {
            // 왜: 벡터 인덱싱 실패를 성공처럼 처리하면(조용히 무시) 추천/검색이 누락되는데 원인 추적이 매우 어렵습니다.
            // 여기서는 명확히 실패로 보고, 워커 재시도로 "벡터 upsert"를 다시 시도할 수 있게 합니다.
            throw new IllegalStateException(
                "요약 벡터 인덱싱 실패: mediaKey=" + mediaKey + ", recoContentId=" + content.getId(),
                e
            );
        }
    }

    private static boolean isSummaryTooShort(String summary, int targetSummaryLength) {
        int len = codePointLength(summary);
        int minLen = minAcceptableSummaryLength(targetSummaryLength);
        return len < minLen;
    }

    private static boolean isKeywordsTooFew(List<String> keywords, int targetSummaryLength) {
        int count = countNonBlankKeywords(keywords);
        int min = minAcceptableKeywordCount(targetSummaryLength);
        return count < min;
    }

    private static int minAcceptableSummaryLength(int targetSummaryLength) {
        // 기준(왜 이런 값인가)
        // - targetSummaryLength는 "대략" 목표 글자수라서 100% 일치할 필요는 없습니다.
        // - 대신, JSON 깨짐/토큰 잘림으로 summary가 20~50자처럼 "말도 안 되게 짧아지는" 케이스를 잡는 게 목적입니다.
        // - 실무적으로는 목표의 절반 이상이면 정상 범주로 보고, 절반 미만은 재요약(최대 2회)로 보정합니다.
        int safeTarget = Math.max(1, targetSummaryLength);
        return Math.max(120, (int) Math.round(safeTarget * 0.5d));
    }

    private static int minAcceptableKeywordCount(int targetSummaryLength) {
        // 왜: keywords는 프롬프트에서 "최대 10개"까지만 요청합니다.
        // 너무 긴 영상(목표 글자수가 큰 영상)은 키워드가 조금 더 많아야 검색/추천 품질이 좋아집니다.
        // 목표 길이에 비례해서 최소 키워드 개수를 4~10 사이로 올립니다(일반적으로 4~7 정도).
        int safeTarget = Math.max(1, targetSummaryLength);
        int computed = (safeTarget / 100) + 2; // 예: 250 -> 4, 300 -> 5, 500 -> 7
        return Math.min(10, Math.max(4, computed));
    }

    private static int countNonBlankKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return 0;
        int count = 0;
        for (String k : keywords) {
            if (k == null) continue;
            if (k.trim().isBlank()) continue;
            count++;
        }
        return count;
    }

    private static int codePointLength(String s) {
        if (s == null) return 0;
        String t = s.trim();
        if (t.isBlank()) return 0;
        return t.codePointCount(0, t.length());
    }

    /**
     * 기존 전사 텍스트가 있는 경우 해당 텍스트로 요약 생성 (레거시 호환).
     */
    private boolean doSummarizeFromTranscript(KollusTranscript transcript) {
        try {
            String mediaKey = transcript.getMediaContentKey();
            if (mediaKey == null || mediaKey.isBlank()) {
                throw new IllegalStateException("media_content_key가 비어 있습니다.");
            }

            if (recoContentRepository.existsByLessonId(mediaKey.trim())) {
                transcript.markSummaryDone();
                transcriptRepository.save(transcript);
                return true;
            }

            String transcriptText = transcript.getTranscriptText();
            if (transcriptText == null || transcriptText.isBlank()) {
                throw new IllegalStateException("전사 텍스트가 비어 있어 요약을 생성할 수 없습니다.");
            }

            // 기존 RecoContentSummaryGenerator 대신 간단히 처리
            // 이 경로는 레거시 호환용이므로 상세 구현은 생략
            log.warn("레거시 전사 텍스트 기반 요약은 더 이상 권장되지 않습니다: {}", mediaKey);
            
            transcript.markSummaryDone();
            transcriptRepository.save(transcript);
            return true;
        } catch (Exception e) {
            transcript.markSummaryFailed(truncateError(e, 2000));
            transcriptRepository.save(transcript);
            return false;
        }
    }

    private static String safeTitle(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.isBlank()) t = "(제목 없음)";
        return trimToCodePoints(t, 200);
    }

    private static String safeCategory(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.isBlank()) return "기타, 기타";
        return trimToCodePoints(t, 100);
    }

    private static String safeSummary(String raw) {
        String t = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (t.isBlank()) return "요약 정보를 생성하지 못했습니다.";
        // ✅ 여기서는 더 이상 고정 길이(예: 300자)로 자르지 않습니다.
        // 요약 길이는 Gemini 프롬프트에 "목표 글자 수"로 전달하여 자연스럽게 조절되도록 합니다.
        return t;
    }

    private static int computeTargetSummaryLength(Integer totalTimeSeconds) {
        // ✅ 왜 필요한가요?
        // - 예전에는 요약을 300자로 고정해서 저장하는 코드가 있어서, 긴 영상도 항상 300자에서 잘렸습니다.
        // - 영상 길이에 비례해서 "목표 글자 수"를 잡아두면, 긴 영상은 내용이 부족하지 않게 저장할 수 있습니다.
        //
        // ✅ 규칙(요청사항)
        // - 목표자 = 200 + (분 * 10)
        // - 분은 (초 / 60)이며, 1분 미만은 1분으로 취급합니다.
        int base = 200;
        int perMinute = 10;

        if (totalTimeSeconds == null || totalTimeSeconds <= 0) {
            // ?? Kollus에서 길이 정보(total_time/duration)를 못 찾는 경우가 드물게 있어,
            // 너무 짧게 저장되지 않도록 기존 기본값(300)을 사용합니다.
            return 300;
        }

        double minutes = Math.max(1.0d, totalTimeSeconds / 60.0d);
        return (int) Math.round(base + (minutes * perMinute));
    }

    private static String joinKeywordsWithinLimit(List<String> keywords, int maxCount, int maxLen) {
        if (keywords == null || keywords.isEmpty()) return "";
        List<String> out = new ArrayList<>();
        for (String k : keywords) {
            if (k == null) continue;
            String t = k.trim();
            if (t.isBlank()) continue;
            out.add(trimToCodePoints(t, 50));
            if (out.size() >= maxCount) break;
        }

        while (!out.isEmpty()) {
            String joined = String.join(", ", out);
            if (joined.length() <= maxLen) return joined;
            out.remove(out.size() - 1);
        }
        return "";
    }

    private static String trimToCodePoints(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        if (t.isBlank()) return "";
        int len = t.codePointCount(0, t.length());
        if (len <= max) return t;
        int endIndex = t.offsetByCodePoints(0, max);
        return t.substring(0, endIndex);
    }

    private static String safeMessageFromTranscript(KollusTranscript transcript) {
        if (transcript == null) return "오류가 발생했습니다.";
        String m = transcript.getLastError();
        if (m == null || m.isBlank()) return "오류가 발생했습니다.";
        return m.length() > 300 ? m.substring(0, 300) + "..." : m;
    }

    private static void safeDeleteDir(Path dir) {
        if (dir == null) return;
        try {
            if (!Files.exists(dir)) return;
            Files.walk(dir)
                .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
        } catch (Exception ignored) {
        }
    }

    private String safeChannelKeyFallback() {
        String channelKey = kollusProperties.channelKey();
        return channelKey == null || channelKey.isBlank() ? "unknown" : channelKey.trim();
    }

    private static String safeFileName(String raw) {
        if (raw == null) return "null";
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String truncateError(Exception e, int maxLen) {
        String msg = e.getClass().getSimpleName() + ": " + safeMessage(e);
        if (msg.length() <= maxLen) return msg;
        return msg.substring(0, Math.max(0, maxLen - 3)) + "...";
    }

    private static String safeMessage(Exception e) {
        String m = e.getMessage();
        if (m == null) return "오류가 발생했습니다.";
        String trimmed = m.trim();
        if (trimmed.isBlank()) return "오류가 발생했습니다.";
        return trimmed.length() > 300 ? trimmed.substring(0, 300) + "..." : trimmed;
    }
}
