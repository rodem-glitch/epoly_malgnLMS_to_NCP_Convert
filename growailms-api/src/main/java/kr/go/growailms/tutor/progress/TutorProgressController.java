package kr.go.growailms.tutor.progress;

import kr.go.growailms.tutor.common.TutorApiResponse;
import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 왜: tutor_lms/api/progress_*.jsp 3개를 하나의 REST 컨트롤러로 통합합니다.
 *     init.jsp 인증은 TutorAuthInterceptor가 처리하므로, 여기서는 비즈니스 로직만 담당합니다.
 *
 * 엔드포인트 매핑:
 *   progress_summary.jsp   → GET /api/tutor/progress/summary?course_id=
 *   progress_students.jsp  → GET /api/tutor/progress/students?course_id=
 *   progress_detail.jsp    → GET /api/tutor/progress/detail?course_id=&user_id=
 */
@RestController
@RequestMapping("/api/tutor/progress")
public class TutorProgressController {

    private static final Logger log = LoggerFactory.getLogger(TutorProgressController.class);

    private final TutorProgressService progressService;

    public TutorProgressController(TutorProgressService progressService) {
        this.progressService = progressService;
    }

    // ========== progress_summary.jsp ==========
    @GetMapping("/summary")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> progressSummary(
            @RequestParam(name = "course_id") int courseId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("진도 요약 조회: userId={}, courseId={}", auth.userId(), courseId);

        return progressService.getProgressSummary(auth, courseId)
                .map(data -> ResponseEntity.ok(TutorApiResponse.success(data)))
                .orElse(ResponseEntity.ok(TutorApiResponse.error(
                        TutorApiResponse.CODE_NO_EDIT_PERMISSION, "진도 조회 권한이 없습니다.")));
    }

    // ========== progress_students.jsp ==========
    @GetMapping("/students")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> progressStudents(
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "s_keyword", defaultValue = "") String keyword,
            @RequestParam(name = "complete_status", defaultValue = "") String completeStatus,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("수강생 진도 목록: userId={}, courseId={}", auth.userId(), courseId);

        List<Map<String, Object>> students = progressService.listProgressStudents(auth, courseId,
                keyword, completeStatus, page, pageSize);
        return ResponseEntity.ok(TutorApiResponse.success(students, students.size()));
    }

    // ========== progress_detail.jsp ==========
    @GetMapping("/detail")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> progressDetail(
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "user_id") int userId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("수강생 진도 상세: authUserId={}, courseId={}, targetUserId={}", auth.userId(), courseId, userId);

        return progressService.getProgressDetail(auth, courseId, userId)
                .map(data -> ResponseEntity.ok(TutorApiResponse.success(data)))
                .orElse(ResponseEntity.ok(TutorApiResponse.notFound("수강생 진도")));
    }
}
