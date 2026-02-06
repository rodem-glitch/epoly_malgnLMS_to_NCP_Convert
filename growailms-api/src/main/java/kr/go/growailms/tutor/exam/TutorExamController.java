package kr.go.growailms.tutor.exam;

import kr.go.growailms.tutor.common.TutorApiResponse;
import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 왜: tutor_lms/api/exam_*.jsp 12개를 하나의 REST 컨트롤러로 통합합니다.
 *     init.jsp 인증은 TutorAuthInterceptor가 처리하므로, 여기서는 비즈니스 로직만 담당합니다.
 *
 * 엔드포인트 매핑:
 *   exam_list.jsp             → GET    /api/tutor/exams?course_id=
 *   exam_insert.jsp           → POST   /api/tutor/exams
 *   exam_modify.jsp           → POST   /api/tutor/exams/{examId}
 *   exam_delete.jsp           → POST   /api/tutor/exams/{examId}/delete
 *   exam_link.jsp             → POST   /api/tutor/exams/{examId}/link
 *   exam_score_update.jsp     → POST   /api/tutor/exams/{examId}/score
 *   exam_submit_cancel.jsp    → POST   /api/tutor/exams/{examId}/submit-cancel
 *   exam_users.jsp            → GET    /api/tutor/exams/{examId}/users?course_id=
 *   exam_template_list.jsp    → GET    /api/tutor/exam-templates
 *   exam_template_insert.jsp  → POST   /api/tutor/exam-templates
 *   exam_template_modify.jsp  → POST   /api/tutor/exam-templates/{id}
 *   exam_template_delete.jsp  → POST   /api/tutor/exam-templates/{id}/delete
 */
@RestController
@RequestMapping("/api/tutor")
public class TutorExamController {

    private static final Logger log = LoggerFactory.getLogger(TutorExamController.class);

    private final TutorExamService examService;

    public TutorExamController(TutorExamService examService) {
        this.examService = examService;
    }

    // ========== exam_list.jsp ==========
    @GetMapping("/exams")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listExams(
            @RequestParam(name = "course_id") int courseId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("시험 목록 조회: userId={}, courseId={}", auth.userId(), courseId);

        if (courseId <= 0) {
            return ResponseEntity.ok(TutorApiResponse.missingParam("course_id"));
        }

        List<Map<String, Object>> list = examService.listExams(auth, courseId);
        if (list == null) {
            // 왜: null은 권한 없음 또는 과목 없음을 의미합니다.
            return ResponseEntity.ok(TutorApiResponse.error(
                    TutorApiResponse.CODE_NO_EDIT_PERMISSION, "해당 과목의 시험 정보를 조회할 권한이 없습니다."));
        }
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }

    // ========== exam_insert.jsp ==========
    @PostMapping("/exams")
    public ResponseEntity<TutorApiResponse<Long>> insertExam(
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "title") String title,
            @RequestParam(name = "examDate") String examDate,
            @RequestParam(name = "examTime") String examTime,
            @RequestParam(name = "duration", defaultValue = "60") int duration,
            @RequestParam(name = "description", defaultValue = "") String description,
            @RequestParam(name = "questionCount", defaultValue = "0") int questionCount,
            @RequestParam(name = "totalScore", defaultValue = "100") int totalScore,
            @RequestParam(name = "allowRetake", defaultValue = "false") String allowRetake,
            @RequestParam(name = "showResults", defaultValue = "true") String showResults,
            @RequestParam(name = "onoff_type", defaultValue = "F") String onoffType
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("시험 등록: userId={}, courseId={}, title={}", auth.userId(), courseId, title);

        if (courseId <= 0) {
            return ResponseEntity.ok(TutorApiResponse.missingParam("course_id"));
        }

        Map<String, Object> result = examService.insertExam(auth, courseId, title, examDate, examTime,
                duration, description, questionCount, totalScore, allowRetake, showResults, onoffType);

        String code = (String) result.get("code");
        if (!TutorApiResponse.CODE_SUCCESS.equals(code)) {
            return ResponseEntity.ok(TutorApiResponse.error(code, (String) result.get("message")));
        }

        Long newId = ((Number) result.get("data")).longValue();
        return ResponseEntity.ok(TutorApiResponse.success(newId));
    }

    // ========== exam_modify.jsp ==========
    @PostMapping("/exams/{examId}")
    public ResponseEntity<TutorApiResponse<Integer>> modifyExam(
            @PathVariable("examId") int examId,
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "title") String title,
            @RequestParam(name = "examDate") String examDate,
            @RequestParam(name = "examTime") String examTime,
            @RequestParam(name = "duration", defaultValue = "60") int duration,
            @RequestParam(name = "description", defaultValue = "") String description,
            @RequestParam(name = "questionCount", defaultValue = "0") int questionCount,
            @RequestParam(name = "totalScore", defaultValue = "100") int totalScore,
            @RequestParam(name = "allowRetake", defaultValue = "false") String allowRetake,
            @RequestParam(name = "showResults", defaultValue = "true") String showResults,
            @RequestParam(name = "onoff_type", defaultValue = "") String onoffType
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("시험 수정: userId={}, courseId={}, examId={}", auth.userId(), courseId, examId);

        if (courseId <= 0 || examId <= 0) {
            return ResponseEntity.ok(TutorApiResponse.missingParam("course_id, exam_id"));
        }

        Map<String, Object> result = examService.modifyExam(auth, courseId, examId, title, examDate, examTime,
                duration, description, questionCount, totalScore, allowRetake, showResults, onoffType);

        String code = (String) result.get("code");
        if (!TutorApiResponse.CODE_SUCCESS.equals(code)) {
            return ResponseEntity.ok(TutorApiResponse.error(code, (String) result.get("message")));
        }

        return ResponseEntity.ok(TutorApiResponse.success(examId));
    }

    // ========== exam_delete.jsp ==========
    @PostMapping("/exams/{examId}/delete")
    public ResponseEntity<TutorApiResponse<Integer>> deleteExam(
            @PathVariable("examId") int examId,
            @RequestParam(name = "course_id") int courseId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("시험 삭제: userId={}, courseId={}, examId={}", auth.userId(), courseId, examId);

        if (courseId <= 0 || examId <= 0) {
            return ResponseEntity.ok(TutorApiResponse.missingParam("course_id, exam_id"));
        }

        Map<String, Object> result = examService.deleteExam(auth, courseId, examId);

        String code = (String) result.get("code");
        if (!TutorApiResponse.CODE_SUCCESS.equals(code)) {
            return ResponseEntity.ok(TutorApiResponse.error(code, (String) result.get("message")));
        }

        return ResponseEntity.ok(TutorApiResponse.success(examId));
    }

    // ========== exam_link.jsp ==========
    @PostMapping("/exams/{examId}/link")
    public ResponseEntity<TutorApiResponse<Integer>> linkExam(
            @PathVariable("examId") int examId,
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "start_date", defaultValue = "") String startDate,
            @RequestParam(name = "end_date", defaultValue = "") String endDate,
            @RequestParam(name = "assign_score", defaultValue = "100") int assignScore,
            @RequestParam(name = "retry_yn", defaultValue = "N") String retryYn,
            @RequestParam(name = "result_yn", defaultValue = "Y") String resultYn
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("시험 연결: userId={}, courseId={}, examId={}", auth.userId(), courseId, examId);

        if (courseId <= 0 || examId <= 0) {
            return ResponseEntity.ok(TutorApiResponse.missingParam("course_id, exam_id"));
        }

        Map<String, Object> result = examService.linkExam(auth, courseId, examId,
                startDate, endDate, assignScore, retryYn, resultYn);

        String code = (String) result.get("code");
        if (!TutorApiResponse.CODE_SUCCESS.equals(code)) {
            return ResponseEntity.ok(TutorApiResponse.error(code, (String) result.get("message")));
        }

        return ResponseEntity.ok(TutorApiResponse.success(examId));
    }

    // ========== exam_score_update.jsp ==========
    @PostMapping("/exams/{examId}/score")
    public ResponseEntity<TutorApiResponse<Integer>> updateScore(
            @PathVariable("examId") int examId,
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "course_user_id") int courseUserId,
            @RequestParam(name = "marking_score") double markingScore
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("시험 점수 저장: userId={}, courseId={}, examId={}, courseUserId={}, markingScore={}",
                auth.userId(), courseId, examId, courseUserId, markingScore);

        if (courseId <= 0 || examId <= 0 || courseUserId <= 0) {
            return ResponseEntity.ok(TutorApiResponse.missingParam("course_id, exam_id, course_user_id"));
        }

        Map<String, Object> result = examService.updateScore(auth, courseId, examId, courseUserId, markingScore);

        String code = (String) result.get("code");
        if (!TutorApiResponse.CODE_SUCCESS.equals(code)) {
            return ResponseEntity.ok(TutorApiResponse.error(code, (String) result.get("message")));
        }

        return ResponseEntity.ok(TutorApiResponse.success(courseUserId));
    }

    // ========== exam_submit_cancel.jsp ==========
    @PostMapping("/exams/{examId}/submit-cancel")
    public ResponseEntity<TutorApiResponse<Integer>> cancelSubmit(
            @PathVariable("examId") int examId,
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "course_user_id") int courseUserId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("시험 응시 취소: userId={}, courseId={}, examId={}, courseUserId={}",
                auth.userId(), courseId, examId, courseUserId);

        if (courseId <= 0 || examId <= 0 || courseUserId <= 0) {
            return ResponseEntity.ok(TutorApiResponse.missingParam("course_id, exam_id, course_user_id"));
        }

        Map<String, Object> result = examService.cancelSubmit(auth, courseId, examId, courseUserId);

        String code = (String) result.get("code");
        if (!TutorApiResponse.CODE_SUCCESS.equals(code)) {
            return ResponseEntity.ok(TutorApiResponse.error(code, (String) result.get("message")));
        }

        return ResponseEntity.ok(TutorApiResponse.success(courseUserId));
    }

    // ========== exam_users.jsp ==========
    @GetMapping("/exams/{examId}/users")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> listExamUsers(
            @PathVariable("examId") int examId,
            @RequestParam(name = "course_id") int courseId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("시험 제출현황 조회: userId={}, courseId={}, examId={}", auth.userId(), courseId, examId);

        if (courseId <= 0 || examId <= 0) {
            return ResponseEntity.ok(TutorApiResponse.missingParam("course_id, exam_id"));
        }

        Map<String, Object> result = examService.listExamUsers(auth, courseId, examId);

        String code = (String) result.get("code");
        if (!TutorApiResponse.CODE_SUCCESS.equals(code)) {
            return ResponseEntity.ok(TutorApiResponse.error(code, (String) result.get("message")));
        }

        // 왜: JSP에서는 rst_data와 rst_exam을 별도로 내려보냈으므로, 하나의 Map으로 묶어서 반환합니다.
        Map<String, Object> responseData = new java.util.LinkedHashMap<>();
        responseData.put("users", result.get("data"));
        responseData.put("exam", result.get("exam"));
        return ResponseEntity.ok(TutorApiResponse.success(responseData));
    }

    // ========== exam_template_list.jsp ==========
    @GetMapping("/exam-templates")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listExamTemplates(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("시험 템플릿 목록 조회: userId={}, page={}, limit={}", auth.userId(), page, limit);

        Map<String, Object> result = examService.listExamTemplates(auth, page, limit);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
        int count = data != null ? data.size() : 0;
        int total = (int) result.get("total");

        // 왜: JSP에서는 rst_total, rst_page, rst_limit을 별도로 내려보냈으므로,
        //     TutorApiResponse에 추가 정보를 담기 위해 data에 페이지 정보를 포함합니다.
        TutorApiResponse<List<Map<String, Object>>> response = new TutorApiResponse<>(
                TutorApiResponse.CODE_SUCCESS,
                "성공 (total:" + total + ", page:" + result.get("page") + ")",
                count,
                data
        );
        return ResponseEntity.ok(response);
    }

    // ========== exam_template_insert.jsp ==========
    @PostMapping("/exam-templates")
    public ResponseEntity<TutorApiResponse<Long>> insertExamTemplate(
            @RequestParam(name = "exam_nm") String examName,
            @RequestParam(name = "exam_time", defaultValue = "60") int examTime,
            @RequestParam(name = "shuffle_yn", defaultValue = "N") String shuffleYn,
            @RequestParam(name = "passing_score", defaultValue = "0") int passingScore,
            @RequestParam(name = "question_ids", defaultValue = "") String questionIds,
            @RequestParam(name = "content", defaultValue = "") String content
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("시험 템플릿 등록: userId={}, examName={}", auth.userId(), examName);

        Map<String, Object> result = examService.insertExamTemplate(auth, examName, examTime,
                shuffleYn, passingScore, questionIds, content);

        String code = (String) result.get("code");
        if (!TutorApiResponse.CODE_SUCCESS.equals(code)) {
            return ResponseEntity.ok(TutorApiResponse.error(code, (String) result.get("message")));
        }

        Long newId = ((Number) result.get("data")).longValue();
        return ResponseEntity.ok(TutorApiResponse.success(newId));
    }

    // ========== exam_template_modify.jsp ==========
    @PostMapping("/exam-templates/{id}")
    public ResponseEntity<TutorApiResponse<Integer>> modifyExamTemplate(
            @PathVariable("id") int id,
            @RequestParam(name = "exam_nm", defaultValue = "") String examName,
            @RequestParam(name = "exam_time", defaultValue = "0") int examTime,
            @RequestParam(name = "shuffle_yn", defaultValue = "") String shuffleYn,
            @RequestParam(name = "passing_score", defaultValue = "0") int passingScore,
            @RequestParam(name = "question_ids", defaultValue = "") String questionIds,
            @RequestParam(name = "content", defaultValue = "") String content
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("시험 템플릿 수정: userId={}, id={}", auth.userId(), id);

        if (id <= 0) {
            return ResponseEntity.ok(TutorApiResponse.missingParam("id"));
        }

        Map<String, Object> result = examService.modifyExamTemplate(auth, id, examName, examTime,
                shuffleYn, passingScore, questionIds, content);

        String code = (String) result.get("code");
        if (!TutorApiResponse.CODE_SUCCESS.equals(code)) {
            return ResponseEntity.ok(TutorApiResponse.error(code, (String) result.get("message")));
        }

        return ResponseEntity.ok(TutorApiResponse.success(id));
    }

    // ========== exam_template_delete.jsp ==========
    @PostMapping("/exam-templates/{id}/delete")
    public ResponseEntity<TutorApiResponse<Integer>> deleteExamTemplate(
            @PathVariable("id") int id
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("시험 템플릿 삭제: userId={}, id={}", auth.userId(), id);

        if (id <= 0) {
            return ResponseEntity.ok(TutorApiResponse.missingParam("id"));
        }

        Map<String, Object> result = examService.deleteExamTemplate(auth, id);

        String code = (String) result.get("code");
        if (!TutorApiResponse.CODE_SUCCESS.equals(code)) {
            return ResponseEntity.ok(TutorApiResponse.error(code, (String) result.get("message")));
        }

        return ResponseEntity.ok(TutorApiResponse.success(id));
    }
}
