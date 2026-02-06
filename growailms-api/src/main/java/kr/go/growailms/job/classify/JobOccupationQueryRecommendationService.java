package kr.go.growailms.job.classify;

import kr.go.growailms.global.vector.service.VectorStoreService;
import kr.go.growailms.global.vector.service.dto.VectorSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class JobOccupationQueryRecommendationService {
    // 왜: 사용자가 입력한 자연어(쿼리)를 임베딩해, "표준 직무 소분류(occupationcode depth3)" 중 유사한 코드를 추천하기 위함입니다.
    // - 채용 검색 파라미터(occupation)는 결국 코드이므로, 자연어를 코드로 바꾸는 단계가 필요합니다.
    // - 이 서비스는 "추천"만 담당하고, 실제 공고 조회/병합은 JobService가 담당합니다.

    private static final Logger log = LoggerFactory.getLogger(JobOccupationQueryRecommendationService.class);
    private static final String FILTER_EXPRESSION = "source == 'occupationcode'";

    private final JobOccupationVectorIndexService jobOccupationVectorIndexService;
    private final VectorStoreService vectorStoreService;

    public JobOccupationQueryRecommendationService(
        JobOccupationVectorIndexService jobOccupationVectorIndexService,
        VectorStoreService vectorStoreService
    ) {
        this.jobOccupationVectorIndexService = Objects.requireNonNull(jobOccupationVectorIndexService);
        this.vectorStoreService = Objects.requireNonNull(vectorStoreService);
    }

    public List<RecommendedOccupation> recommendTopK(String query, int topK) {
        String q = toString(query);
        if (q.isBlank()) {
            throw new IllegalArgumentException("자연어 추천 실패: query(q)가 비어 있습니다.");
        }
        int safeTopK = Math.max(1, Math.min(topK, 10));

        try {
            jobOccupationVectorIndexService.ensureIndexed();
        } catch (Exception e) {
            // 왜: 폴백으로 조용히 넘어가면 화면에서는 "그냥 결과가 이상함"만 보이므로, 실패는 즉시 드러나게 합니다.
            log.error("자연어 추천 벡터 색인 준비 실패: query={}", q, e);
            throw new IllegalStateException("자연어 추천 실패: 벡터 색인 준비 실패", e);
        }

        List<VectorSearchResult> results;
        try {
            results = vectorStoreService.similaritySearch(q, safeTopK, 0.0, FILTER_EXPRESSION);
        } catch (Exception e) {
            log.error("자연어 추천 벡터 검색 실패: query={}, topK={}", q, safeTopK, e);
            throw new IllegalStateException("자연어 추천 실패: 벡터 검색 실패", e);
        }

        if (results == null || results.isEmpty()) {
            log.error("자연어 추천 결과 0건: query={}, topK={}", q, safeTopK);
            throw new IllegalStateException("자연어 추천 실패: 추천 결과 0건");
        }

        // 왜: 벡터 검색은 같은 standard_code가 여러 번 나올 수 있으니, 중복을 제거하고 점수는 첫 번째(최상위)를 사용합니다.
        Map<String, RecommendedOccupation> unique = new LinkedHashMap<>();
        for (VectorSearchResult r : results) {
            if (r == null || r.metadata() == null) continue;
            String standardCode = toString(r.metadata().get("standard_code"));
            if (!looksLikeDepth3Code(standardCode)) continue;

            if (!unique.containsKey(standardCode)) {
                unique.put(standardCode, new RecommendedOccupation(
                    standardCode,
                    toString(r.metadata().get("depth1_name")),
                    toString(r.metadata().get("depth2_name")),
                    toString(r.metadata().get("depth3_name")),
                    r.score()
                ));
            }
        }

        List<RecommendedOccupation> out = new ArrayList<>(unique.values());
        if (out.isEmpty()) {
            Map<String, Object> meta0 = null;
            try {
                meta0 = (results.get(0) == null) ? null : results.get(0).metadata();
            } catch (Exception ignored) {
                meta0 = null;
            }
            log.error(
                "자연어 추천 usable 결과 0건(standard_code 없음): query={}, metaKeys={}",
                q,
                (meta0 == null ? List.of() : meta0.keySet())
            );
            throw new IllegalStateException("자연어 추천 실패: usable 추천 결과 0건");
        }

        // 적극 로그(디버깅): 상위 결과를 짧게 요약합니다.
        StringBuilder sb = new StringBuilder();
        int max = Math.min(3, out.size());
        for (int i = 0; i < max; i++) {
            RecommendedOccupation o = out.get(i);
            if (o == null) continue;
            if (i > 0) sb.append(", ");
            sb.append(o.standardCode()).append("(").append(nullToDash(o.depth3Name())).append(")");
            sb.append(":").append(String.format("%.3f", o.score()));
        }
        log.info("자연어 추천 top{}: query='{}' -> {}", max, q, sb.toString());

        return out;
    }

    private boolean looksLikeDepth3Code(String raw) {
        if (raw == null) return false;
        String v = raw.trim();
        if (v.isBlank()) return false;
        return v.matches("^\\d{6}$");
    }

    private String toString(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value).trim();
        return s.isBlank() ? "" : s;
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    public record RecommendedOccupation(
        String standardCode,
        String depth1Name,
        String depth2Name,
        String depth3Name,
        double score
    ) {
    }
}
