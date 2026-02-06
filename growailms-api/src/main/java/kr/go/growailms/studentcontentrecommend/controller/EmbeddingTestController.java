package kr.go.growailms.studentcontentrecommend.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import kr.go.growailms.global.vector.service.VectorQueryService;
import kr.go.growailms.global.vector.service.dto.VectorSearchResult;
import kr.go.growailms.recocontent.entity.RecoContent;
import kr.go.growailms.recocontent.repository.RecoContentRepository;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 임베딩 추천 테스트 전용 컨트롤러.
 * 왜: 키워드 검색을 완전히 제외하고 순수 벡터 임베딩만으로 추천 품질을 테스트합니다.
 */
@RestController
@RequestMapping("/student/content-recommend/test")
@CrossOrigin(origins = "*")
public class EmbeddingTestController {

    private final VectorQueryService vectorQueryService;
    private final RecoContentRepository recoContentRepository;

    public EmbeddingTestController(
        VectorQueryService vectorQueryService,
        RecoContentRepository recoContentRepository
    ) {
        this.vectorQueryService = Objects.requireNonNull(vectorQueryService);
        this.recoContentRepository = Objects.requireNonNull(recoContentRepository);
    }

    /**
     * 순수 임베딩만으로 검색하는 테스트 API.
     * 왜: 키워드 검색 로직을 완전히 제외하여 임베딩 품질만 독립적으로 테스트합니다.
     */
    @PostMapping("/embedding-only")
    public EmbeddingTestResponse searchEmbeddingOnly(@RequestBody EmbeddingTestRequest request) {
        String query = request.query() == null ? "" : request.query().trim();
        if (query.isBlank()) {
            return new EmbeddingTestResponse(query, List.of(), 0);
        }

        int topK = request.topK() == null ? 10 : Math.max(1, Math.min(request.topK(), 50));
        double threshold = request.threshold() == null ? 0.0 : Math.max(0.0, Math.min(request.threshold(), 1.0));

        // 키워드 검색 없이 순수 벡터 검색만 수행
        List<VectorSearchResult> vectorResults = vectorQueryService.similaritySearchWithQueryTaskType(
            query,
            topK,
            threshold,
            "source == 'tb_reco_content'"
        );

        // RecoContent에서 상세 정보 조회
        Map<Long, RecoContent> contentById = fetchRecoContentsById(vectorResults);

        List<EmbeddingTestResult> results = new ArrayList<>();
        for (VectorSearchResult vr : vectorResults) {
            Map<String, Object> meta = vr.metadata() == null ? new HashMap<>() : vr.metadata();
            Long contentId = toLong(meta.get("content_id"));

            String title = null;
            String categoryNm = null;
            String keywords = null;
            String summary = null;

            if (contentId != null) {
                RecoContent content = contentById.get(contentId);
                if (content != null) {
                    title = content.getTitle();
                    categoryNm = content.getCategoryNm();
                    keywords = content.getKeywords();
                    summary = content.getSummary();
                }
            }

            // fallback
            if (title == null) {
                title = meta.get("title") != null ? String.valueOf(meta.get("title")) : null;
            }
            if (categoryNm == null) {
                categoryNm = meta.get("category_nm") != null ? String.valueOf(meta.get("category_nm")) : null;
            }

            results.add(new EmbeddingTestResult(
                contentId,
                title,
                categoryNm,
                keywords,
                summary,
                vr.score()
            ));
        }

        return new EmbeddingTestResponse(query, results, results.size());
    }

    /**
     * 테스트 케이스 목록을 반환하는 API.
     * 왜: 프론트엔드에서 자동 테스트에 사용할 15개 예시를 제공합니다.
     */
    @GetMapping("/cases")
    public List<TestCase> getTestCases() {
        return List.of(
            new TestCase("프로그래밍", List.of("프로그래밍", "코딩", "개발", "파이썬", "자바")),
            new TestCase("파이썬 데이터 분석", List.of("파이썬", "데이터", "분석", "판다스")),
            new TestCase("마케팅 전략", List.of("마케팅", "전략", "광고", "브랜드")),
            new TestCase("인공지능", List.of("AI", "인공지능", "머신러닝", "딥러닝")),
            new TestCase("엑셀 활용", List.of("엑셀", "스프레드시트", "오피스", "데이터")),
            new TestCase("자격증 시험", List.of("자격증", "시험", "정보처리", "자격")),
            new TestCase("리더십", List.of("리더십", "경영", "조직", "팀")),
            new TestCase("영어 회화", List.of("영어", "회화", "어학", "비즈니스")),
            new TestCase("재무회계", List.of("회계", "재무", "세무", "경리")),
            new TestCase("웹 개발", List.of("웹", "HTML", "JavaScript", "프론트엔드")),
            new TestCase("자기계발", List.of("자기계발", "습관", "독서", "시간관리")),
            new TestCase("디자인", List.of("디자인", "UI", "UX", "그래픽")),
            new TestCase("부동산 투자", List.of("부동산", "투자", "재테크", "갭투자")),
            new TestCase("건강 관리", List.of("건강", "운동", "다이어트", "헬스")),
            new TestCase("창업", List.of("창업", "스타트업", "비즈니스", "사업"))
        );
    }

    private Map<Long, RecoContent> fetchRecoContentsById(List<VectorSearchResult> results) {
        if (results == null || results.isEmpty()) return Map.of();

        Set<Long> ids = new HashSet<>();
        for (VectorSearchResult result : results) {
            if (result == null || result.metadata() == null) continue;
            Long id = toLong(result.metadata().get("content_id"));
            if (id != null) ids.add(id);
        }
        if (ids.isEmpty()) return Map.of();

        Map<Long, RecoContent> map = new HashMap<>();
        for (RecoContent content : recoContentRepository.findAllById(ids)) {
            if (content == null || content.getId() == null) continue;
            map.put(content.getId(), content);
        }
        return map;
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try {
            String s = String.valueOf(value).trim();
            if (s.isBlank()) return null;
            return Long.parseLong(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    public record EmbeddingTestRequest(
        String query,
        Integer topK,
        Double threshold
    ) {}

    public record EmbeddingTestResponse(
        String query,
        List<EmbeddingTestResult> results,
        int totalCount
    ) {}

    public record EmbeddingTestResult(
        Long contentId,
        String title,
        String categoryNm,
        String keywords,
        String summary,
        double score
    ) {}

    public record TestCase(
        String query,
        List<String> expectedKeywords
    ) {}
}
