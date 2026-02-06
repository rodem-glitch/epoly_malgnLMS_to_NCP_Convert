package kr.go.growailms.job.classify;

import kr.go.growailms.global.vector.service.VectorStoreService;
import kr.go.growailms.global.vector.service.dto.VectorSearchResult;
import kr.go.growailms.job.repository.JobRepository;
import kr.go.growailms.job.repository.JobRepository.OccupationLookupRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class JobKoreaOccupationCodeRecommendationService {
    // 왜: Work24(표준 직무명) → 잡코리아(rpcd) 변환을 사람이 수동으로 보정하지 않기 위해,
    //      "표준 직무명"과 "잡코리아 코드명"을 임베딩으로 비교해 가장 가까운 rpcd를 자동 추천합니다.

    private static final Logger log = LoggerFactory.getLogger(JobKoreaOccupationCodeRecommendationService.class);

    private static final String FILTER_RPCD = "source == 'jobkorea_code' && code_type == 'rpcd'";
    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_THRESHOLD = 0.0;

    private static final int CACHE_MAX_SIZE = 2000;

    private final JobRepository jobRepository;
    private final JobKoreaOccupationVectorIndexService jobKoreaOccupationVectorIndexService;
    private final VectorStoreService vectorStoreService;

    private final Map<String, List<String>> cache = Collections.synchronizedMap(
        new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
                return size() > CACHE_MAX_SIZE;
            }
        }
    );

    public JobKoreaOccupationCodeRecommendationService(
        JobRepository jobRepository,
        JobKoreaOccupationVectorIndexService jobKoreaOccupationVectorIndexService,
        VectorStoreService vectorStoreService
    ) {
        this.jobRepository = Objects.requireNonNull(jobRepository);
        this.jobKoreaOccupationVectorIndexService = Objects.requireNonNull(jobKoreaOccupationVectorIndexService);
        this.vectorStoreService = Objects.requireNonNull(vectorStoreService);
    }

    public List<String> recommendRpcdCodesByStandardCode(String standardOccupationCode) {
        String requested = toString(standardOccupationCode);
        if (requested.isBlank()) {
            throw new IllegalArgumentException("잡코리아 코드 추천 실패: 표준 직무 코드가 비었습니다.");
        }

        List<String> cached = cache.get(requested);
        if (cached != null) return cached;

        String queryText = buildQueryText(requested);
        if (queryText.isBlank()) {
            throw new IllegalStateException("잡코리아 코드 추천 실패: 검색용 텍스트가 비었습니다. standardCode=" + requested);
        }

        try {
            jobKoreaOccupationVectorIndexService.ensureIndexed();
        } catch (Exception e) {
            // 왜: 폴백(무필터/빈 추천)으로 넘어가면 오류가 가려져 디버깅이 어려워집니다. 실패는 즉시 에러로 노출합니다.
            log.error("잡코리아 코드 벡터 색인 준비 실패: requestedStandardCode={}", requested, e);
            throw new IllegalStateException("잡코리아 코드 추천 실패: 벡터 색인 준비 실패. standardCode=" + requested, e);
        }

        List<VectorSearchResult> results;
        try {
            results = vectorStoreService.similaritySearch(queryText, DEFAULT_TOP_K, DEFAULT_THRESHOLD, FILTER_RPCD);
        } catch (Exception e) {
            log.error("잡코리아 코드 추천 벡터 검색 실패: requestedStandardCode={}", requested, e);
            throw new IllegalStateException("잡코리아 코드 추천 실패: 벡터 검색 실패. standardCode=" + requested, e);
        }

        if (results == null || results.isEmpty()) {
            // 왜: 추천이 비면 이후 단계(잡코리아 조회)가 무의미해져 문제를 숨기게 됩니다.
            log.error("잡코리아 코드 추천 결과가 비었습니다: requestedStandardCode={}", requested);
            throw new IllegalStateException("잡코리아 코드 추천 실패: 추천 결과 0건. standardCode=" + requested);
        }

        Set<String> out = new LinkedHashSet<>();
        for (VectorSearchResult r : results) {
            if (r == null || r.metadata() == null) continue;
            Object code = r.metadata().get("code");
            String v = toString(code);
            if (v.isBlank()) continue;
            out.add(v);
        }

        List<String> list = List.copyOf(out);
        if (list.isEmpty()) {
            log.error("잡코리아 코드 추천 결과에서 usable rpcd가 없습니다: requestedStandardCode={}", requested);
            throw new IllegalStateException("잡코리아 코드 추천 실패: usable rpcd 0건. standardCode=" + requested);
        }
        cache.put(requested, list);
        return list;
    }

    private String buildQueryText(String standardOccupationCode) {
        Optional<OccupationLookupRow> lookupOpt = jobRepository.findOccupationLookupByCode(standardOccupationCode);
        if (lookupOpt.isEmpty()) {
            return standardOccupationCode;
        }

        OccupationLookupRow row = lookupOpt.get();
        String depth1 = toString(row.depth1());
        String depth2 = toString(row.depth2());
        String depth3 = toString(row.depth3());

        StringBuilder sb = new StringBuilder();
        if (!depth1.isBlank()) sb.append("대분류: ").append(depth1).append("\n");
        if (!depth2.isBlank()) sb.append("중분류: ").append(depth2).append("\n");
        if (!depth3.isBlank()) sb.append("소분류: ").append(depth3).append("\n");
        return sb.toString().trim();
    }

    private String toString(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value).trim();
        return s.isBlank() ? "" : s;
    }
}
