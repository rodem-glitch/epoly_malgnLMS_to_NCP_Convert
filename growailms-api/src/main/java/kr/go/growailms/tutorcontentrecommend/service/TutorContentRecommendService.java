package kr.go.growailms.tutorcontentrecommend.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import kr.go.growailms.global.vector.service.VectorStoreService;
import kr.go.growailms.global.vector.service.dto.VectorSearchResult;
import kr.go.growailms.recocontent.entity.RecoContent;
import kr.go.growailms.recocontent.repository.RecoContentRepository;
import kr.go.growailms.tutorcontentrecommend.service.dto.TutorContentRecommendRequest;
import kr.go.growailms.tutorcontentrecommend.service.dto.TutorContentRecommendResponse;
import org.springframework.stereotype.Service;

@Service
public class TutorContentRecommendService {

    private final VectorStoreService vectorStoreService;
    private final RecoContentRepository recoContentRepository;

    public TutorContentRecommendService(
        VectorStoreService vectorStoreService,
        RecoContentRepository recoContentRepository
    ) {
        // 왜: 벡터 DB(Qdrant) 검색은 공통 VectorStoreService로 통일해서, 추천 도메인별로 재사용합니다.
        this.vectorStoreService = Objects.requireNonNull(vectorStoreService);
        this.recoContentRepository = Objects.requireNonNull(recoContentRepository);
    }

    public List<TutorContentRecommendResponse> recommendLessons(TutorContentRecommendRequest request) {
        TutorContentRecommendRequest safe = request == null ? TutorContentRecommendRequest.empty() : request;

        String query = buildQueryText(safe);
        String filterExpression = "source == 'tb_reco_content'";

        List<VectorSearchResult> results = vectorStoreService.similaritySearch(
            query,
            safe.topKOrDefault(),
            safe.similarityThresholdOrDefault(),
            filterExpression
        );

        Map<Long, RecoContent> contentById = fetchRecoContentsById(results);
        return results.stream()
            .map(r -> toResponse(r, contentById))
            .toList();
    }

    private String buildQueryText(TutorContentRecommendRequest request) {
        // 왜: 교수자가 입력한 "과목/차시" 정보는 영상 추천에 필요한 힌트이므로, 자연어 문장으로 합쳐 임베딩 쿼리를 만듭니다.
        StringBuilder sb = new StringBuilder();

        appendIfPresent(sb, "과목명", request.courseName());
        appendIfPresent(sb, "과목소개", request.courseIntro());
        appendIfPresent(sb, "과목세부내용", request.courseDetail());
        appendIfPresent(sb, "차시제목", request.lessonTitle());
        appendIfPresent(sb, "차시설명", request.lessonDescription());
        appendIfPresent(sb, "키워드", request.keywords());

        if (sb.length() == 0) {
            // 왜: 아무것도 안 들어오면 벡터 검색이 의미가 없어서, 최소한의 기본 문장을 제공합니다.
            return "과목 개설에 적합한 교육용 영상을 추천해 주세요.";
        }

        sb.append("요청: 위 내용을 학습하는 데 도움이 되는 기존 교육용 영상을 추천해 주세요.");
        return sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value == null) return;
        String trimmed = value.trim();
        if (trimmed.isBlank()) return;
        sb.append(label).append(": ").append(trimmed).append("\n");
    }

    private TutorContentRecommendResponse toResponse(VectorSearchResult result, Map<Long, RecoContent> contentById) {
        Map<String, Object> meta = result.metadata() == null ? new HashMap<>() : new HashMap<>(result.metadata());

        String lessonId = toStringValue(meta.get("lesson_id"));
        Long recoContentId = toLong(meta.get("content_id"));

        // 왜: 벡터 메타데이터에는 summary가 없을 수 있으므로, DB에서 직접 조회합니다.
        String title = null;
        String category = null;
        String summary = null;
        String keywords = null;

        if (recoContentId != null) {
            // 왜: topK가 커져도 DB 조회가 N번 반복되지 않도록, 미리 한 번에 조회한 Map을 사용합니다.
            RecoContent content = contentById == null ? null : contentById.get(recoContentId);
            if (content != null) {
                title = content.getTitle();
                category = content.getCategoryNm();
                summary = content.getSummary();
                keywords = content.getKeywords();
                if (lessonId == null) {
                    lessonId = content.getLessonId();
                }
            }
        }

        // 왜: DB 조회 실패 시 메타데이터에서 fallback
        if (title == null) {
            title = meta.get("title") != null ? String.valueOf(meta.get("title")) : null;
        }
        if (category == null) {
            category = meta.get("category_nm") != null ? String.valueOf(meta.get("category_nm")) : null;
        }

        return new TutorContentRecommendResponse(
            lessonId,
            recoContentId,
            title,
            category,
            summary,
            keywords,
            result.score(),
            meta
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

    private String toStringValue(Object value) {
        // 왜: 콜러스 영상 키값은 '5vcd73vW' 같은 문자열이므로, 그대로 String으로 변환합니다.
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isBlank() ? null : s;
    }
}
