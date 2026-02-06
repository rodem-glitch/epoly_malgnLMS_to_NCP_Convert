package kr.go.growailms.tutor.grades;

import kr.go.growailms.tutor.common.TutorApiResponse;
import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 왜: tutor_lms/api/grades_*.jsp 2개를 하나의 REST 컨트롤러로 통합합니다.
 *     init.jsp 인증은 TutorAuthInterceptor가 처리하므로, 여기서는 비즈니스 로직만 담당합니다.
 *
 * 엔드포인트 매핑:
 *   grades_list.jsp   → GET  /api/tutor/grades?course_id=
 *   grades_recalc.jsp → POST /api/tutor/grades/recalc
 */
@RestController
@RequestMapping("/api/tutor/grades")
public class TutorGradesController {

    private static final Logger log = LoggerFactory.getLogger(TutorGradesController.class);

    private final TutorGradesService gradesService;

    public TutorGradesController(TutorGradesService gradesService) {
        this.gradesService = gradesService;
    }

    // ========== grades_list.jsp ==========
    @GetMapping
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listGrades(
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "s_keyword", defaultValue = "") String keyword
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("성적 목록 조회: userId={}, courseId={}", auth.userId(), courseId);

        List<Map<String, Object>> list = gradesService.listGrades(auth, courseId, keyword);
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }

    // ========== grades_recalc.jsp ==========
    @PostMapping("/recalc")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> recalcGrades(
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "course_user_id", defaultValue = "0") int courseUserId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("성적 재계산: userId={}, courseId={}, courseUserId={}", auth.userId(), courseId, courseUserId);

        // 왜: course_user_id = 0이면 과목 전체 재계산, 0보다 크면 해당 수강생만 재계산
        return gradesService.recalcGrades(auth, courseId, courseUserId)
                .map(data -> ResponseEntity.ok(TutorApiResponse.success(data)))
                .orElse(ResponseEntity.ok(TutorApiResponse.error(
                        TutorApiResponse.CODE_NO_EDIT_PERMISSION, "해당 과목의 성적을 처리할 권한이 없습니다.")));
    }
}
