package kr.go.growailms.tutor.completion;

import kr.go.growailms.tutor.common.TutorApiResponse;
import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 왜: tutor_lms/api/completion_*.jsp 2개를 하나의 REST 컨트롤러로 통합합니다.
 *     init.jsp 인증은 TutorAuthInterceptor가 처리하므로, 여기서는 비즈니스 로직만 담당합니다.
 *
 * 엔드포인트 매핑:
 *   completion_list.jsp   → GET  /api/tutor/completions?course_id=
 *   completion_update.jsp → POST /api/tutor/completions/update
 */
@RestController
@RequestMapping("/api/tutor/completions")
public class TutorCompletionController {

    private static final Logger log = LoggerFactory.getLogger(TutorCompletionController.class);

    private final TutorCompletionService completionService;

    public TutorCompletionController(TutorCompletionService completionService) {
        this.completionService = completionService;
    }

    // ========== completion_list.jsp ==========
    @GetMapping
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listCompletions(
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "s_keyword", defaultValue = "") String keyword,
            @RequestParam(name = "complete_status", defaultValue = "") String completeStatus
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("수료 목록 조회: userId={}, courseId={}", auth.userId(), courseId);

        List<Map<String, Object>> list = completionService.listCompletions(auth, courseId, keyword, completeStatus);
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }

    // ========== completion_update.jsp ==========
    @PostMapping("/update")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> updateCompletion(
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "action") String action,
            @RequestParam(name = "course_user_ids") String courseUserIds
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("수료 상태 변경: userId={}, courseId={}, action={}", auth.userId(), courseId, action);

        // 왜: action = complete_y, complete_n, close_y, close_n
        Map<String, Object> result = completionService.updateCompletion(auth, courseId, action, courseUserIds);
        if (result.containsKey("error")) {
            return ResponseEntity.ok(TutorApiResponse.error(
                    (String) result.get("code"), (String) result.get("error")));
        }
        return ResponseEntity.ok(TutorApiResponse.success(result));
    }
}
