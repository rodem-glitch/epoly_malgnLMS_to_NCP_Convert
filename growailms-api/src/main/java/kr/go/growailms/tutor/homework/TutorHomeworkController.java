package kr.go.growailms.tutor.homework;

import kr.go.growailms.tutor.common.TutorApiResponse;
import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 왜: tutor_lms/api/homework_*.jsp 11개를 하나의 REST 컨트롤러로 통합합니다.
 *     init.jsp 인증은 TutorAuthInterceptor가 처리하므로, 여기서는 비즈니스 로직만 담당합니다.
 *
 * 엔드포인트 매핑:
 *   homework_list.jsp           → GET    /api/tutor/homeworks?course_id=
 *   homework_insert.jsp         → POST   /api/tutor/homeworks
 *   homework_modify.jsp         → POST   /api/tutor/homeworks/{id}
 *   homework_delete.jsp         → POST   /api/tutor/homeworks/{id}/delete
 *   homework_feedback_update.jsp→ POST   /api/tutor/homeworks/{id}/feedback
 *   homework_submissions.jsp    → GET    /api/tutor/homework-submissions
 *   homework_submit_cancel.jsp  → POST   /api/tutor/homeworks/{id}/submit-cancel
 *   homework_task_append.jsp    → POST   /api/tutor/homeworks/{id}/tasks
 *   homework_task_confirm.jsp   → POST   /api/tutor/homework-tasks/{taskId}/confirm
 *   homework_task_list.jsp      → GET    /api/tutor/homeworks/{id}/tasks
 *   homework_users.jsp          → GET    /api/tutor/homeworks/{id}/users?course_id=
 */
@RestController
@RequestMapping("/api/tutor")
public class TutorHomeworkController {

    private static final Logger log = LoggerFactory.getLogger(TutorHomeworkController.class);

    private final TutorHomeworkService homeworkService;

    public TutorHomeworkController(TutorHomeworkService homeworkService) {
        this.homeworkService = homeworkService;
    }

    // ========== homework_list.jsp ==========
    @GetMapping("/homeworks")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listHomeworks(
            @RequestParam(name = "course_id") int courseId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("과제 목록 조회: userId={}, courseId={}", auth.userId(), courseId);

        Map<String, Object> result = homeworkService.listHomeworks(auth, courseId);
        String code = (String) result.get("rst_code");
        if (!TutorApiResponse.CODE_SUCCESS.equals(code)) {
            return ResponseEntity.ok(TutorApiResponse.error(code, (String) result.get("rst_message")));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("rst_data");
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }

    // ========== homework_insert.jsp ==========
    @PostMapping("/homeworks")
    public ResponseEntity<TutorApiResponse<Long>> insertHomework(
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "title") String title,
            @RequestParam(name = "description") String description,
            @RequestParam(name = "dueDate") String dueDate,
            @RequestParam(name = "dueTime") String dueTime,
            @RequestParam(name = "totalScore", defaultValue = "100") int totalScore,
            @RequestParam(name = "onoff_type", defaultValue = "N") String onoffType
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("과제 등록: userId={}, courseId={}, title={}", auth.userId(), courseId, title);

        Map<String, Object> result = homeworkService.insertHomework(
                auth, courseId, title, description, dueDate, dueTime, totalScore, onoffType);
        String code = (String) result.get("rst_code");
        if (!TutorApiResponse.CODE_SUCCESS.equals(code)) {
            return ResponseEntity.ok(TutorApiResponse.error(code, (String) result.get("rst_message")));
        }

        long newId = ((Number) result.get("rst_data")).longValue();
        return ResponseEntity.ok(TutorApiResponse.success(newId));
    }

    // ========== homework_modify.jsp ==========
    @PostMapping("/homeworks/{id}")
    public ResponseEntity<TutorApiResponse<Integer>> modifyHomework(
            @PathVariable("id") int homeworkId,
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "title") String title,
            @RequestParam(name = "description", defaultValue = "") String description,
            @RequestParam(name = "dueDate") String dueDate,
            @RequestParam(name = "dueTime") String dueTime,
            @RequestParam(name = "totalScore", defaultValue = "100") int totalScore,
            @RequestParam(name = "onoff_type", defaultValue = "N") String onoffType
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("과제 수정: userId={}, courseId={}, homeworkId={}", auth.userId(), courseId, homeworkId);

        Map<String, Object> result = homeworkService.modifyHomework(
                auth, courseId, homeworkId, title, description, dueDate, dueTime, totalScore, onoffType);
        String code = (String) result.get("rst_code");
        if (!TutorApiResponse.CODE_SUCCESS.equals(code)) {
            return ResponseEntity.ok(TutorApiResponse.error(code, (String) result.get("rst_message")));
        }

        return ResponseEntity.ok(TutorApiResponse.success(homeworkId));
    }

    // ========== homework_delete.jsp ==========
    @PostMapping("/homeworks/{id}/delete")
    public ResponseEntity<TutorApiResponse<Integer>> deleteHomework(
            @PathVariable("id") int homeworkId,
            @RequestParam(name = "course_id") int courseId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("과제 삭제: userId={}, courseId={}, homeworkId={}", auth.userId(), courseId, homeworkId);

        Map<String, Object> result = homeworkService.deleteHomework(auth, courseId, homeworkId);
        String code = (String) result.get("rst_code");
        if (!TutorApiResponse.CODE_SUCCESS.equals(code)) {
            return ResponseEntity.ok(TutorApiResponse.error(code, (String) result.get("rst_message")));
        }

        return ResponseEntity.ok(TutorApiResponse.success(homeworkId));
    }

    // ========== homework_feedback_update.jsp ==========
    @PostMapping("/homeworks/{id}/feedback")
    public ResponseEntity<TutorApiResponse<Integer>> updateFeedback(
            @PathVariable("id") int homeworkId,
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "course_user_id") int courseUserId,
            @RequestParam(name = "marking_score") double markingScore,
            @RequestParam(name = "feedback", defaultValue = "") String feedback
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("과제 피드백: userId={}, courseId={}, homeworkId={}, courseUserId={}",
                auth.userId(), courseId, homeworkId, courseUserId);

        Map<String, Object> result = homeworkService.updateFeedback(
                auth, courseId, homeworkId, courseUserId, markingScore, feedback);
        String code = (String) result.get("rst_code");
        if (!TutorApiResponse.CODE_SUCCESS.equals(code)) {
            return ResponseEntity.ok(TutorApiResponse.error(code, (String) result.get("rst_message")));
        }

        return ResponseEntity.ok(TutorApiResponse.success(courseUserId));
    }

    // ========== homework_submissions.jsp ==========
    @GetMapping("/homework-submissions")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listSubmissions(
            @RequestParam(name = "s_keyword", defaultValue = "") String keyword,
            @RequestParam(name = "start_date", defaultValue = "") String startDate,
            @RequestParam(name = "end_date", defaultValue = "") String endDate,
            @RequestParam(name = "status", defaultValue = "") String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("과제 제출 통합목록: userId={}, keyword={}, page={}", auth.userId(), keyword, page);

        Map<String, Object> result = homeworkService.listSubmissions(
                auth, keyword, startDate, endDate, status, page, pageSize);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("rst_data");
        int totalCount = ((Number) result.get("rst_total")).intValue();

        // 왜: 통합 제출 목록은 페이지네이션 정보(rst_total, rst_page, rst_limit)를 추가로 반환합니다.
        TutorApiResponse<List<Map<String, Object>>> response = new TutorApiResponse<>(
                TutorApiResponse.CODE_SUCCESS, "성공", totalCount, data);
        return ResponseEntity.ok(response);
    }

    // ========== homework_submit_cancel.jsp ==========
    @PostMapping("/homeworks/{id}/submit-cancel")
    public ResponseEntity<TutorApiResponse<Integer>> cancelSubmission(
            @PathVariable("id") int homeworkId,
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "course_user_id") int courseUserId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("과제 제출취소: userId={}, courseId={}, homeworkId={}, courseUserId={}",
                auth.userId(), courseId, homeworkId, courseUserId);

        Map<String, Object> result = homeworkService.cancelSubmission(auth, courseId, homeworkId, courseUserId);
        String code = (String) result.get("rst_code");
        if (!TutorApiResponse.CODE_SUCCESS.equals(code)) {
            return ResponseEntity.ok(TutorApiResponse.error(code, (String) result.get("rst_message")));
        }

        return ResponseEntity.ok(TutorApiResponse.success(courseUserId));
    }

    // ========== homework_task_append.jsp ==========
    @PostMapping("/homeworks/{id}/tasks")
    public ResponseEntity<TutorApiResponse<Long>> appendTask(
            @PathVariable("id") int homeworkId,
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "course_user_id") int courseUserId,
            @RequestParam(name = "task") String task
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("추가 과제 부여: userId={}, courseId={}, homeworkId={}, courseUserId={}",
                auth.userId(), courseId, homeworkId, courseUserId);

        Map<String, Object> result = homeworkService.appendTask(
                auth, courseId, homeworkId, courseUserId, task);
        String code = (String) result.get("rst_code");
        if (!TutorApiResponse.CODE_SUCCESS.equals(code)) {
            return ResponseEntity.ok(TutorApiResponse.error(code, (String) result.get("rst_message")));
        }

        long newId = ((Number) result.get("rst_data")).longValue();
        return ResponseEntity.ok(TutorApiResponse.success(newId));
    }

    // ========== homework_task_confirm.jsp ==========
    @PostMapping("/homework-tasks/{taskId}/confirm")
    public ResponseEntity<TutorApiResponse<Void>> confirmTask(
            @PathVariable("taskId") int taskId,
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "feedback", defaultValue = "") String feedback
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("추가 과제 확인: userId={}, taskId={}, courseId={}", auth.userId(), taskId, courseId);

        Map<String, Object> result = homeworkService.confirmTask(auth, courseId, taskId, feedback);
        String code = (String) result.get("rst_code");
        if (!TutorApiResponse.CODE_SUCCESS.equals(code)) {
            return ResponseEntity.ok(TutorApiResponse.error(code, (String) result.get("rst_message")));
        }

        return ResponseEntity.ok(TutorApiResponse.success());
    }

    // ========== homework_task_list.jsp ==========
    @GetMapping("/homeworks/{id}/tasks")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listTasks(
            @PathVariable("id") int homeworkId,
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "course_user_id") int courseUserId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("추가 과제 목록: userId={}, courseId={}, homeworkId={}, courseUserId={}",
                auth.userId(), courseId, homeworkId, courseUserId);

        Map<String, Object> result = homeworkService.listTasks(auth, courseId, homeworkId, courseUserId);
        String code = (String) result.get("rst_code");
        if (!TutorApiResponse.CODE_SUCCESS.equals(code)) {
            return ResponseEntity.ok(TutorApiResponse.error(code, (String) result.get("rst_message")));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("rst_data");
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }

    // ========== homework_users.jsp ==========
    @GetMapping("/homeworks/{id}/users")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> listHomeworkUsers(
            @PathVariable("id") int homeworkId,
            @RequestParam(name = "course_id") int courseId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("과제 수강생 현황: userId={}, courseId={}, homeworkId={}", auth.userId(), courseId, homeworkId);

        Map<String, Object> result = homeworkService.listHomeworkUsers(auth, courseId, homeworkId);
        String code = (String) result.get("rst_code");
        if (!TutorApiResponse.CODE_SUCCESS.equals(code)) {
            return ResponseEntity.ok(TutorApiResponse.error(code, (String) result.get("rst_message")));
        }

        // 왜: homework_users.jsp는 rst_data(학생 목록)와 rst_homework(과제 정보)를 함께 반환합니다.
        return ResponseEntity.ok(TutorApiResponse.success(result));
    }
}
