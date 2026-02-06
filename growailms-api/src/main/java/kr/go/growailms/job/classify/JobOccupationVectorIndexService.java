package kr.go.growailms.job.classify;

import kr.go.growailms.global.vector.service.VectorStoreService;
import kr.go.growailms.job.repository.JobRepository;
import kr.go.growailms.job.repository.JobRepository.OccupationDepth3VectorRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class JobOccupationVectorIndexService {
    // 왜: 잡코리아/Work24의 직무 코드 체계가 달라서, "공고 텍스트 → 표준 소분류"로 재분류하려면
    //      표준 소분류(occupationcode depth3) 자체를 벡터 DB에 먼저 색인해 둬야 합니다.

    private static final Logger log = LoggerFactory.getLogger(JobOccupationVectorIndexService.class);
    private static final String FILTER_EXPRESSION = "source == 'occupationcode'";

    private final JobRepository jobRepository;
    private final VectorStoreService vectorStoreService;

    private volatile boolean indexAttempted = false;
    private volatile boolean indexed = false;

    public JobOccupationVectorIndexService(JobRepository jobRepository, VectorStoreService vectorStoreService) {
        this.jobRepository = Objects.requireNonNull(jobRepository);
        this.vectorStoreService = Objects.requireNonNull(vectorStoreService);
    }

    public synchronized void ensureIndexed() {
        if (indexed) return;
        if (indexAttempted) return;
        indexAttempted = true;

        try {
            // 왜: 이미 색인되어 있으면(운영/개발에서 1회 적재 후) 중복 작업을 피합니다.
            if (hasAnyIndexedDocuments()) {
                indexed = true;
                return;
            }
        } catch (Exception e) {
            // 왜: 벡터 DB/임베딩 설정이 아직 없을 수 있으니, 여기서 예외가 나도 서버 전체를 죽이지 않습니다.
            log.warn("직무 소분류 벡터 색인 여부 확인 실패(일단 미색인으로 진행)", e);
        }

        List<OccupationDepth3VectorRow> rows = jobRepository.findAllOccupationDepth3VectorRows();
        if (rows.isEmpty()) {
            log.warn("직무 소분류 벡터 색인 대상이 없습니다(occupationcode).");
            return;
        }

        log.info("직무 소분류 벡터 색인 시작: 대상 {}건", rows.size());
        int indexedCount = 0;
        for (OccupationDepth3VectorRow row : rows) {
            if (row == null) continue;
            String code = safe(row.depth3Code());
            if (code.isBlank()) continue;

            String docId = "occupationcode:" + code;
            String text = buildEmbeddingText(row);
            Map<String, Object> meta = buildMetadata(row);

            vectorStoreService.upsertText(docId, text, meta);
            indexedCount++;

            if (indexedCount % 100 == 0) {
                log.info("직무 소분류 벡터 색인 진행: {} / {}", indexedCount, rows.size());
            }
        }

        indexed = true;
        log.info("직무 소분류 벡터 색인 완료: {}건", indexedCount);
    }

    private boolean hasAnyIndexedDocuments() {
        // 왜: query 1회만으로 "occupationcode 문서가 이미 들어있는지" 빠르게 판단합니다.
        return !vectorStoreService.similaritySearch("직무", 1, 0.0, FILTER_EXPRESSION).isEmpty();
    }

    private Map<String, Object> buildMetadata(OccupationDepth3VectorRow row) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("source", "occupationcode");
        meta.put("standard_code", safe(row.depth3Code()));
        meta.put("depth1_code", safe(row.depth1Code()));
        meta.put("depth2_code", safe(row.depth2Code()));
        meta.put("depth1_name", safe(row.depth1Name()));
        meta.put("depth2_name", safe(row.depth2Name()));
        meta.put("depth3_name", safe(row.depth3Name()));
        meta.put("indexed_at", Instant.now().getEpochSecond());
        return meta;
    }

    private String buildEmbeddingText(OccupationDepth3VectorRow row) {
        // 왜: 벡터 검색 품질을 올리려면, 대/중/소 명칭을 한 문서로 묶어 임베딩하는 방식이 안전합니다.
        String depth1 = safe(row.depth1Name());
        String depth2 = safe(row.depth2Name());
        String depth3 = safe(row.depth3Name());

        StringBuilder sb = new StringBuilder();
        if (!depth1.isBlank()) sb.append("대분류: ").append(depth1).append("\n");
        if (!depth2.isBlank()) sb.append("중분류: ").append(depth2).append("\n");
        if (!depth3.isBlank()) sb.append("소분류: ").append(depth3).append("\n");
        return sb.toString().trim();
    }

    private static String safe(String value) {
        if (value == null) return "";
        String v = value.trim();
        return v.isBlank() ? "" : v;
    }
}
