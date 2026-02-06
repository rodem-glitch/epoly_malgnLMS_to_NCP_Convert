package kr.go.growailms.tutor.lesson;

import kr.go.growailms.tutor.common.TutorApiResponse;
import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 왜: tutor_lms/api/external_link_lesson_upsert.jsp를 REST 컨트롤러로 변환합니다.
 *     init.jsp 인증은 TutorAuthInterceptor가 처리하므로, 여기서는 비즈니스 로직만 담당합니다.
 *
 * 엔드포인트 매핑:
 *   external_link_lesson_upsert.jsp → POST /api/tutor/lessons/external-link
 *
 * 왜: 교수 차시관리에서 콜러스 영상 외에 "외부링크(URL)"를 직접 입력하여 레슨으로 등록합니다.
 *     lesson_type = '04' (외부링크)
 */
@RestController
@RequestMapping("/api/tutor/lessons")
public class TutorLessonController {

    private static final Logger log = LoggerFactory.getLogger(TutorLessonController.class);

    private final TutorLessonService lessonService;

    public TutorLessonController(TutorLessonService lessonService) {
        this.lessonService = lessonService;
    }

    // ========== external_link_lesson_upsert.jsp ==========
    @PostMapping("/external-link")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> upsertExternalLink(
            @RequestParam(name = "url") String url,
            @RequestParam(name = "title") String title,
            @RequestParam(name = "total_time", defaultValue = "0") int totalTime
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("외부링크 레슨 등록/수정: userId={}, url={}, title={}", auth.userId(), url, title);

        Map<String, Object> result = lessonService.upsertExternalLink(auth, url, title, totalTime);
        if (result.containsKey("error")) {
            return ResponseEntity.ok(TutorApiResponse.error(
                    (String) result.get("code"), (String) result.get("error")));
        }
        return ResponseEntity.ok(TutorApiResponse.success(result));
    }
}
