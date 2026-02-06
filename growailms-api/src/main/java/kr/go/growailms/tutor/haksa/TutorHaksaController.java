package kr.go.growailms.tutor.haksa;

import kr.go.growailms.tutor.common.TutorApiResponse;
import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 왜: tutor_lms/api/haksa_*.jsp 11개를 하나의 REST 컨트롤러로 통합합니다.
 *     학사 시스템은 5-part 복합키(course_code, open_year, open_term, bunban_code, group_code)를 사용합니다.
 *
 * 엔드포인트 매핑:
 *   haksa_resolve.jsp          → POST  /api/tutor/haksa/resolve
 *   haksa_students_list.jsp    → GET   /api/tutor/haksa/students
 *   haksa_attendance.jsp       → GET   /api/tutor/haksa/attendance
 *   haksa_curriculum_get.jsp   → GET   /api/tutor/haksa/curriculum
 *   haksa_curriculum_update.jsp→ POST  /api/tutor/haksa/curriculum
 *   haksa_course_eval_get.jsp  → GET   /api/tutor/haksa/evaluation
 *   haksa_course_eval_update   → POST  /api/tutor/haksa/evaluation
 *   haksa_exam_get.jsp         → GET   /api/tutor/haksa/exams
 *   haksa_exam_update.jsp      → POST  /api/tutor/haksa/exams
 *   haksa_grade_list.jsp       → GET   /api/tutor/haksa/grades
 *   haksa_grade_update.jsp     → POST  /api/tutor/haksa/grades
 */
@RestController
@RequestMapping("/api/tutor/haksa")
public class TutorHaksaController {

    private static final Logger log = LoggerFactory.getLogger(TutorHaksaController.class);

    private final TutorHaksaService haksaService;

    public TutorHaksaController(TutorHaksaService haksaService) {
        this.haksaService = haksaService;
    }

    // ========== haksa_resolve.jsp ==========
    // 왜: 학사 과목을 LMS 과목으로 매핑합니다. 없으면 자동 생성합니다.
    @PostMapping("/resolve")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> resolve(
            @RequestParam("course_code") String courseCode,
            @RequestParam("open_year") String openYear,
            @RequestParam("open_term") String openTerm,
            @RequestParam(name = "bunban_code", defaultValue = "") String bunbanCode,
            @RequestParam(name = "group_code", defaultValue = "") String groupCode
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("학사 과목 resolve: courseCode={}, openYear={}, openTerm={}", courseCode, openYear, openTerm);

        Map<String, Object> result = haksaService.resolve(auth, courseCode, openYear, openTerm, bunbanCode, groupCode);
        return ResponseEntity.ok(TutorApiResponse.success(result));
    }

    // ========== haksa_students_list.jsp ==========
    @GetMapping("/students")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listStudents(
            @RequestParam("course_code") String courseCode,
            @RequestParam("open_year") String openYear,
            @RequestParam("open_term") String openTerm,
            @RequestParam(name = "bunban_code", defaultValue = "") String bunbanCode,
            @RequestParam(name = "group_code", defaultValue = "") String groupCode,
            @RequestParam(name = "s_keyword", defaultValue = "") String keyword
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        List<Map<String, Object>> students = haksaService.listStudents(auth,
                courseCode, openYear, openTerm, bunbanCode, groupCode, keyword);
        return ResponseEntity.ok(TutorApiResponse.success(students, students.size()));
    }

    // ========== haksa_attendance.jsp ==========
    @GetMapping("/attendance")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> getAttendance(
            @RequestParam("course_code") String courseCode,
            @RequestParam("open_year") String openYear,
            @RequestParam("open_term") String openTerm,
            @RequestParam(name = "bunban_code", defaultValue = "") String bunbanCode,
            @RequestParam(name = "group_code", defaultValue = "") String groupCode,
            @RequestParam(name = "session_id", defaultValue = "0") int sessionId,
            @RequestParam(name = "course_id", defaultValue = "0") int courseId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        Map<String, Object> attendance = haksaService.getAttendance(auth,
                courseCode, openYear, openTerm, bunbanCode, groupCode, sessionId, courseId);
        return ResponseEntity.ok(TutorApiResponse.success(attendance));
    }

    // ========== haksa_curriculum_get.jsp ==========
    @GetMapping("/curriculum")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> getCurriculum(
            @RequestParam("course_code") String courseCode,
            @RequestParam("open_year") String openYear,
            @RequestParam("open_term") String openTerm,
            @RequestParam(name = "bunban_code", defaultValue = "") String bunbanCode,
            @RequestParam(name = "group_code", defaultValue = "") String groupCode
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        Map<String, Object> curriculum = haksaService.getCurriculum(auth,
                courseCode, openYear, openTerm, bunbanCode, groupCode);
        return ResponseEntity.ok(TutorApiResponse.success(curriculum));
    }

    // ========== haksa_curriculum_update.jsp ==========
    @PostMapping("/curriculum")
    public ResponseEntity<TutorApiResponse<Void>> updateCurriculum(
            @RequestParam("course_code") String courseCode,
            @RequestParam("open_year") String openYear,
            @RequestParam("open_term") String openTerm,
            @RequestParam(name = "bunban_code", defaultValue = "") String bunbanCode,
            @RequestParam(name = "group_code", defaultValue = "") String groupCode,
            @RequestParam("curriculum_json") String curriculumJson
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        haksaService.updateCurriculum(auth, courseCode, openYear, openTerm, bunbanCode, groupCode, curriculumJson);
        return ResponseEntity.ok(TutorApiResponse.success());
    }

    // ========== haksa_course_eval_get.jsp ==========
    @GetMapping("/evaluation")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> getEvaluation(
            @RequestParam("course_code") String courseCode,
            @RequestParam("open_year") String openYear,
            @RequestParam("open_term") String openTerm,
            @RequestParam(name = "bunban_code", defaultValue = "") String bunbanCode,
            @RequestParam(name = "group_code", defaultValue = "") String groupCode
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        Map<String, Object> eval = haksaService.getEvaluation(auth,
                courseCode, openYear, openTerm, bunbanCode, groupCode);
        return ResponseEntity.ok(TutorApiResponse.success(eval));
    }

    // ========== haksa_course_eval_update.jsp ==========
    @PostMapping("/evaluation")
    public ResponseEntity<TutorApiResponse<Void>> updateEvaluation(
            @RequestParam("course_code") String courseCode,
            @RequestParam("open_year") String openYear,
            @RequestParam("open_term") String openTerm,
            @RequestParam(name = "bunban_code", defaultValue = "") String bunbanCode,
            @RequestParam(name = "group_code", defaultValue = "") String groupCode,
            @RequestParam("eval_json") String evalJson
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        haksaService.updateEvaluation(auth, courseCode, openYear, openTerm, bunbanCode, groupCode, evalJson);
        return ResponseEntity.ok(TutorApiResponse.success());
    }

    // ========== haksa_exam_get.jsp ==========
    @GetMapping("/exams")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> getExams(
            @RequestParam("course_code") String courseCode,
            @RequestParam("open_year") String openYear,
            @RequestParam("open_term") String openTerm,
            @RequestParam(name = "bunban_code", defaultValue = "") String bunbanCode,
            @RequestParam(name = "group_code", defaultValue = "") String groupCode
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        Map<String, Object> exams = haksaService.getExams(auth,
                courseCode, openYear, openTerm, bunbanCode, groupCode);
        return ResponseEntity.ok(TutorApiResponse.success(exams));
    }

    // ========== haksa_exam_update.jsp ==========
    @PostMapping("/exams")
    public ResponseEntity<TutorApiResponse<Void>> updateExams(
            @RequestParam("course_code") String courseCode,
            @RequestParam("open_year") String openYear,
            @RequestParam("open_term") String openTerm,
            @RequestParam(name = "bunban_code", defaultValue = "") String bunbanCode,
            @RequestParam(name = "group_code", defaultValue = "") String groupCode,
            @RequestParam("exams_json") String examsJson
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        haksaService.updateExams(auth, courseCode, openYear, openTerm, bunbanCode, groupCode, examsJson);
        return ResponseEntity.ok(TutorApiResponse.success());
    }

    // ========== haksa_grade_list.jsp ==========
    @GetMapping("/grades")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listGrades(
            @RequestParam("course_code") String courseCode,
            @RequestParam("open_year") String openYear,
            @RequestParam("open_term") String openTerm,
            @RequestParam(name = "bunban_code", defaultValue = "") String bunbanCode,
            @RequestParam(name = "group_code", defaultValue = "") String groupCode
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        List<Map<String, Object>> grades = haksaService.listGrades(auth,
                courseCode, openYear, openTerm, bunbanCode, groupCode);
        return ResponseEntity.ok(TutorApiResponse.success(grades, grades.size()));
    }

    // ========== haksa_grade_update.jsp ==========
    @PostMapping("/grades")
    public ResponseEntity<TutorApiResponse<Void>> updateGrades(
            @RequestParam("course_code") String courseCode,
            @RequestParam("open_year") String openYear,
            @RequestParam("open_term") String openTerm,
            @RequestParam(name = "bunban_code", defaultValue = "") String bunbanCode,
            @RequestParam(name = "group_code", defaultValue = "") String groupCode,
            @RequestParam("grades_json") String gradesJson
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        haksaService.updateGrades(auth, courseCode, openYear, openTerm, bunbanCode, groupCode, gradesJson);
        return ResponseEntity.ok(TutorApiResponse.success());
    }
}
