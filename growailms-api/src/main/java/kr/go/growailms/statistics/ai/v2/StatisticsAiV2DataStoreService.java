package kr.go.growailms.statistics.ai.v2;

import kr.go.growailms.global.vector.service.VectorStoreService;
import kr.go.growailms.global.vector.service.dto.VectorSearchResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class StatisticsAiV2DataStoreService {
    // 왜: v2는 "자유질문"이므로, LLM이 쓸 수 있는 데이터/연산을 먼저 '찾아주는' 단계가 필요합니다.
    //     1차 버전은 RAG(벡터검색) 없이도 바로 쓸 수 있게, 규칙 기반(키워드)으로 후보를 빠르게 제시합니다.

    private static final String DOMAIN = "statistics_ai_v2";

    private final StatisticsAiV2CatalogService catalogService;
    private final VectorStoreService vectorStoreService;

    public StatisticsAiV2DataStoreService(
            StatisticsAiV2CatalogService catalogService,
            VectorStoreService vectorStoreService
    ) {
        this.catalogService = catalogService;
        this.vectorStoreService = vectorStoreService;
    }

    public StatisticsAiV2DataStoreSearchResponse search(String prompt, Map<String, Object> context) {
        StatisticsAiV2CatalogResponse catalog = catalogService.getCatalog();
        String text = normalize(prompt);

        List<StatisticsAiV2DataStoreSearchResponse.Candidate> dataSources = new ArrayList<>(searchByVector(text, "datasource", 4));
        List<StatisticsAiV2DataStoreSearchResponse.Candidate> operations = new ArrayList<>(searchByVector(text, "operation", 8));

        // 왜: 벡터 검색은 환경(키/벡터DB) 의존이 있으니, 결과가 비거나 실패하면 키워드 기반으로 폴백합니다.
        if (dataSources.isEmpty() || operations.isEmpty()) {
            addDataSourceCandidates(text, catalog, dataSources);
            addOperationCandidates(text, catalog, operations);
        }

        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("admRegions", catalog.admRegions());
        hints.put("campusGroups", catalog.campusGroups());
        hints.put("recommendedYears", catalog.recommendedYears());
        hints.put("industryCategories", catalog.industryCategories());
        hints.put("internalEmploymentAvailableYears", safeMap(catalog.mappings()).get("internalEmploymentAvailableYears"));
        if (context != null && !context.isEmpty()) {
            hints.put("context", context);
        }

        return new StatisticsAiV2DataStoreSearchResponse(
                List.copyOf(dedup(dataSources)),
                List.copyOf(dedup(operations)),
                hints
        );
    }

    private List<StatisticsAiV2DataStoreSearchResponse.Candidate> searchByVector(String prompt, String kind, int max) {
        if (!StringUtils.hasText(prompt)) {
            return List.of();
        }

        try {
            List<VectorSearchResult> results = vectorStoreService.similaritySearch(prompt, 30, 0.0);
            List<StatisticsAiV2DataStoreSearchResponse.Candidate> out = new ArrayList<>();
            for (VectorSearchResult r : results) {
                if (r == null || r.metadata() == null) continue;
                Object domain = r.metadata().get("domain");
                Object k = r.metadata().get("kind");
                if (!DOMAIN.equals(domain)) continue;
                if (kind != null && !kind.equals(k)) continue;

                String id = String.valueOf(r.metadata().getOrDefault("id", ""));
                String name = String.valueOf(r.metadata().getOrDefault("name", id));
                if (!StringUtils.hasText(id)) continue;

                out.add(new StatisticsAiV2DataStoreSearchResponse.Candidate(id, name, "벡터 유사도=" + round2(r.score())));
                if (out.size() >= max) break;
            }
            return List.copyOf(out);
        } catch (Exception e) {
            return List.of();
        }
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private void addDataSourceCandidates(String text, StatisticsAiV2CatalogResponse catalog, List<StatisticsAiV2DataStoreSearchResponse.Candidate> out) {
        if (!StringUtils.hasText(text)) {
            return;
        }

        if (containsAny(text, List.of("인구", "연령", "성별", "인구수"))) {
            out.add(new StatisticsAiV2DataStoreSearchResponse.Candidate("kosis_population", "인구 통계", "질문에 인구/연령/성별 키워드가 있습니다."));
        }
        if (containsAny(text, List.of("산업", "종사자", "사업체", "it산업", "ict", "주력 산업", "성장률", "제조업", "서비스업", "건설업", "도소매", "전국", "지역"))) {
            out.add(new StatisticsAiV2DataStoreSearchResponse.Candidate("sgis_industry", "산업별 종사자 통계", "질문에 산업/종사자/제조업/전국 키워드가 있습니다."));
        }
        if (containsAny(text, List.of("취업", "취업률", "졸업", "양성", "우리", "학교", "내부", "학과"))) {
            out.add(new StatisticsAiV2DataStoreSearchResponse.Candidate("internal_employment", "캠퍼스/학과별 취업률", "질문에 취업/우리/내부/학과 키워드가 있습니다."));
        }
        if (containsAny(text, List.of("입학", "충원", "정원", "입학률", "충원률", "우리", "학교", "내부"))) {
            out.add(new StatisticsAiV2DataStoreSearchResponse.Candidate("internal_admission", "캠퍼스/학과별 입학충원률", "질문에 입학/충원/우리/내부 키워드가 있습니다."));
        }
    }

    private void addOperationCandidates(String text, StatisticsAiV2CatalogResponse catalog, List<StatisticsAiV2DataStoreSearchResponse.Candidate> out) {
        if (!StringUtils.hasText(text)) {
            return;
        }

        if (containsAny(text, List.of("상관", "상관관계", "correlation"))) {
            out.add(new StatisticsAiV2DataStoreSearchResponse.Candidate(StatisticsAiV2Ops.CHEMIST_CORRELATION, "상관관계 계산", "질문에 상관관계 키워드가 있습니다."));
        }
        if (containsAny(text, List.of("성장", "성장률", "증가율", "변화율"))) {
            out.add(new StatisticsAiV2DataStoreSearchResponse.Candidate(StatisticsAiV2Ops.CHEMIST_GROWTH_RATE, "성장률 계산", "질문에 성장률 키워드가 있습니다."));
        }
        if (containsAny(text, List.of("취업률", "증감", "변화", "몇%p"))) {
            out.add(new StatisticsAiV2DataStoreSearchResponse.Candidate(StatisticsAiV2Ops.CHEMIST_DELTA_POINTS, "변화량(%p) 계산", "취업률/변화량(증감) 관련 키워드가 있습니다."));
        }

        if (containsAny(text, List.of("it산업", "ict", "종사자", "사업체", "제조업", "서비스업", "전국", "서울", "부산", "대구", "인천", "광주", "대전", "울산", "경기"))) {
            out.add(new StatisticsAiV2DataStoreSearchResponse.Candidate(StatisticsAiV2Ops.SGIS_METRIC_SERIES, "SGIS 시계열 조회", "산업/종사자/제조업/지역별 데이터를 연도별로 비교할 수 있습니다."));
        }
        if (containsAny(text, List.of("인구", "연령", "성별"))) {
            out.add(new StatisticsAiV2DataStoreSearchResponse.Candidate(StatisticsAiV2Ops.KOSIS_POPULATION_SERIES, "KOSIS 시계열 조회", "인구 데이터를 연도별로 비교할 수 있습니다."));
        }
        if (containsAny(text, List.of("취업률", "it학과", "우리 학과", "우리", "학교", "내부", "학과"))) {
            out.add(new StatisticsAiV2DataStoreSearchResponse.Candidate(StatisticsAiV2Ops.INTERNAL_EMPLOYMENT_SERIES, "내부 취업률 시계열 조회", "내부 취업률을 연도별로 비교할 수 있습니다."));
        }

        out.add(new StatisticsAiV2DataStoreSearchResponse.Candidate(StatisticsAiV2Ops.DESIGNER_CHART, "차트 생성", "결과를 차트로 렌더링할 수 있습니다."));
    }

    private List<StatisticsAiV2DataStoreSearchResponse.Candidate> dedup(List<StatisticsAiV2DataStoreSearchResponse.Candidate> list) {
        Set<String> seen = new LinkedHashSet<>();
        List<StatisticsAiV2DataStoreSearchResponse.Candidate> out = new ArrayList<>();
        for (StatisticsAiV2DataStoreSearchResponse.Candidate c : list) {
            String key = (c == null) ? null : c.id();
            if (!StringUtils.hasText(key)) continue;
            if (seen.add(key)) {
                out.add(c);
            }
        }
        return out;
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String k : keywords) {
            if (StringUtils.hasText(k) && text.contains(normalize(k))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String v) {
        return (v == null) ? "" : v.trim().toLowerCase(Locale.KOREA);
    }

    private Map<String, Object> safeMap(Map<String, Object> v) {
        return (v == null) ? Map.of() : v;
    }
}
