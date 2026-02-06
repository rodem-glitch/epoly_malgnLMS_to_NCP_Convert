package kr.go.growailms.tutor.content;

import kr.go.growailms.tutor.common.TutorApiResponse;
import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 왜: tutor_lms/api/content_recommend.jsp를 REST 컨트롤러로 변환합니다.
 *     init.jsp 인증은 TutorAuthInterceptor가 처리하므로, 여기서는 비즈니스 로직만 담당합니다.
 *
 * 엔드포인트 매핑:
 *   content_recommend.jsp → POST /api/tutor/content/recommend
 *
 * 왜: 교수자 화면(과목/차시 구성)에서 "콘텐츠 검색"을 눌렀을 때,
 *     입력된 과목/차시 정보를 기반으로 추천 목록을 반환합니다.
 *     내부적으로 polytech-lms-api 추천 엔드포인트를 프록시합니다.
 */
@RestController
@RequestMapping("/api/tutor/content")
public class TutorContentController {

    private static final Logger log = LoggerFactory.getLogger(TutorContentController.class);

    private final TutorContentService contentService;

    public TutorContentController(TutorContentService contentService) {
        this.contentService = contentService;
    }

    // ========== content_recommend.jsp ==========
    @PostMapping("/recommend")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> recommend(
            @RequestParam(name = "course_name", defaultValue = "") String courseName,
            @RequestParam(name = "course_intro", defaultValue = "") String courseIntro,
            @RequestParam(name = "course_detail", defaultValue = "") String courseDetail,
            @RequestParam(name = "lesson_title", defaultValue = "") String lessonTitle,
            @RequestParam(name = "lesson_description", defaultValue = "") String lessonDescription,
            @RequestParam(name = "keywords", defaultValue = "") String keywords,
            @RequestParam(name = "top_k", defaultValue = "50") int topK,
            @RequestParam(name = "similarity_threshold", defaultValue = "0.2") double similarityThreshold
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("콘텐츠 추천 요청: userId={}, courseName={}", auth.userId(), courseName);

        // 왜: top_k 상한 제한
        if (topK <= 0) topK = 50;
        if (topK > 50) topK = 50;
        if (similarityThreshold <= 0) similarityThreshold = 0.2;

        List<Map<String, Object>> recommendations = contentService.recommend(auth,
                courseName, courseIntro, courseDetail, lessonTitle, lessonDescription,
                keywords, topK, similarityThreshold);
        return ResponseEntity.ok(TutorApiResponse.success(recommendations, recommendations.size()));
    }
}
