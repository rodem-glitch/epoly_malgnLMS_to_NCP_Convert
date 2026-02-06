package kr.go.growailms.tutor.course;

import kr.go.growailms.tutor.common.TutorApiResponse;
import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 왜: tutor_lms/api/course_*.jsp 20개를 하나의 REST 컨트롤러로 통합합니다.
 *     init.jsp 인증은 TutorAuthInterceptor가 처리하므로, 여기서는 비즈니스 로직만 담당합니다.
 *
 * 엔드포인트 매핑:
 *   course_list.jsp           → GET    /api/tutor/courses
 *   course_insert.jsp         → POST   /api/tutor/courses
 *   course_view.jsp           → GET    /api/tutor/courses/{id}
 *   course_info_get.jsp       → GET    /api/tutor/courses/{id}/info
 *   course_info_update.jsp    → POST   /api/tutor/courses/{id}/info
 *   course_copy.jsp           → POST   /api/tutor/courses/{id}/copy
 *   course_categories.jsp     → GET    /api/tutor/courses/categories
 *   course_years.jsp          → GET    /api/tutor/courses/years
 *   course_certificate_update → POST   /api/tutor/courses/{id}/certificate
 *   course_evaluation_get     → GET    /api/tutor/courses/{id}/evaluation
 *   course_evaluation_update  → POST   /api/tutor/courses/{id}/evaluation
 *   course_feedback_report    → GET    /api/tutor/courses/{id}/feedback-report
 *   course_image_upload       → POST   /api/tutor/courses/{id}/image
 *   course_list_combined      → GET    /api/tutor/courses/combined
 *   course_list_poly          → GET    /api/tutor/courses/poly
 *   course_resolve            → GET    /api/tutor/courses/resolve
 *   course_set_program        → POST   /api/tutor/courses/{id}/program
 *   course_students_list      → GET    /api/tutor/courses/{id}/students
 *   course_students_add       → POST   /api/tutor/courses/{id}/students
 *   course_students_remove    → POST   /api/tutor/courses/{id}/students/remove
 */
@RestController
@RequestMapping("/api/tutor/courses")
public class TutorCourseController {

    private static final Logger log = LoggerFactory.getLogger(TutorCourseController.class);

    private final TutorCourseService courseService;

    public TutorCourseController(TutorCourseService courseService) {
        this.courseService = courseService;
    }

    // ========== course_list.jsp ==========
    @GetMapping
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listCourses(
            @RequestParam(name = "s_keyword", defaultValue = "") String keyword,
            @RequestParam(name = "year", defaultValue = "") String year,
            @RequestParam(name = "tutor_id", defaultValue = "0") int tutorId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("과목 목록 조회: userId={}, keyword={}, year={}", auth.userId(), keyword, year);

        List<Map<String, Object>> list = courseService.listCourses(auth, keyword, year, tutorId);
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }

    // ========== course_insert.jsp ==========
    @PostMapping
    public ResponseEntity<TutorApiResponse<Long>> insertCourse(
            @RequestParam(name = "course_nm") String courseNm,
            @RequestParam(name = "year") String year,
            @RequestParam(name = "study_sdate") String studySdate,
            @RequestParam(name = "study_edate") String studyEdate,
            @RequestParam(name = "tutor_id", defaultValue = "0") int tutorId,
            @RequestParam(name = "program_id", defaultValue = "0") int programId,
            @RequestParam(name = "category_id", defaultValue = "0") int categoryId,
            @RequestParam(name = "semester", defaultValue = "") String semester,
            @RequestParam(name = "credit", defaultValue = "0") int credit,
            @RequestParam(name = "content1", defaultValue = "") String content1,
            @RequestParam(name = "content2", defaultValue = "") String content2,
            @RequestParam(name = "course_file", defaultValue = "") String courseFile
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        // 왜: 교수자는 자기 과목만 만들 수 있고, 관리자는 다른 교수자 과목도 만들 수 있습니다.
        int effectiveTutorId = auth.isAdmin() && tutorId > 0 ? tutorId : (int) auth.userId();

        if (!auth.isAdmin() && tutorId > 0 && tutorId != (int) auth.userId()) {
            return ResponseEntity.status(403).body(
                    TutorApiResponse.error(TutorApiResponse.CODE_NO_EDIT_PERMISSION, "다른 교수자의 과목을 생성할 권한이 없습니다."));
        }

        long newId = courseService.insertCourse(auth, effectiveTutorId, courseNm, year,
                studySdate, studyEdate, programId, categoryId, semester, credit, content1, content2, courseFile);
        return ResponseEntity.ok(TutorApiResponse.success(newId));
    }

    // ========== course_view.jsp / course_info_get.jsp ==========
    @GetMapping("/{id}")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> viewCourse(
            @PathVariable("id") int id
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        return courseService.getCourseDetail(auth, id)
                .map(data -> ResponseEntity.ok(TutorApiResponse.success(data)))
                .orElse(ResponseEntity.ok(TutorApiResponse.notFound("과목")));
    }

    @GetMapping("/{id}/info")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> getCourseInfo(
            @PathVariable("id") int id
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        return courseService.getCourseDetail(auth, id)
                .map(data -> ResponseEntity.ok(TutorApiResponse.success(data)))
                .orElse(ResponseEntity.ok(TutorApiResponse.notFound("과목")));
    }

    // ========== course_info_update.jsp ==========
    @PostMapping("/{id}/info")
    public ResponseEntity<TutorApiResponse<Integer>> updateCourseInfo(
            @PathVariable("id") int id,
            @RequestParam(name = "content1", defaultValue = "") String content1,
            @RequestParam(name = "content2", defaultValue = "") String content2
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        boolean ok = courseService.updateCourseInfo(auth, id, content1, content2);
        if (!ok) {
            return ResponseEntity.ok(TutorApiResponse.error(TutorApiResponse.CODE_NO_EDIT_PERMISSION, "과목 수정 권한이 없습니다."));
        }
        return ResponseEntity.ok(TutorApiResponse.success(id));
    }

    // ========== course_copy.jsp ==========
    @PostMapping("/{id}/copy")
    public ResponseEntity<TutorApiResponse<?>> copyCourse(
            @PathVariable("id") int sourceCourseId,
            @RequestParam(name = "course_nm") String courseNm,
            @RequestParam(name = "tutor_id") int tutorId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        // 왜: 과목 복사는 관리자만 가능합니다.
        if (!auth.isAdmin()) {
            return ResponseEntity.status(403).body(TutorApiResponse.noPermission());
        }
        long newId = courseService.copyCourse(auth, sourceCourseId, courseNm, tutorId);
        return ResponseEntity.ok(TutorApiResponse.success(newId));
    }

    // ========== course_categories.jsp ==========
    @GetMapping("/categories")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listCategories() {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        List<Map<String, Object>> categories = courseService.listCategories(auth.siteId());
        return ResponseEntity.ok(TutorApiResponse.success(categories, categories.size()));
    }

    // ========== course_years.jsp ==========
    @GetMapping("/years")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listYears(
            @RequestParam(name = "tutor_id", defaultValue = "0") int tutorId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        List<Map<String, Object>> years = courseService.listYears(auth, tutorId);
        return ResponseEntity.ok(TutorApiResponse.success(years, years.size()));
    }

    // ========== course_certificate_update.jsp ==========
    @PostMapping("/{id}/certificate")
    public ResponseEntity<TutorApiResponse<Integer>> updateCertificate(
            @PathVariable("id") int id,
            @RequestParam Map<String, String> params
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        boolean ok = courseService.updateCertificate(auth, id, params);
        if (!ok) {
            return ResponseEntity.ok(TutorApiResponse.error(TutorApiResponse.CODE_NO_EDIT_PERMISSION, "수정 권한이 없습니다."));
        }
        return ResponseEntity.ok(TutorApiResponse.success(id));
    }

    // ========== course_evaluation_get.jsp ==========
    @GetMapping("/{id}/evaluation")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> getEvaluation(
            @PathVariable("id") int id
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        return courseService.getEvaluation(auth, id)
                .map(data -> ResponseEntity.ok(TutorApiResponse.success(data)))
                .orElse(ResponseEntity.ok(TutorApiResponse.notFound("과목")));
    }

    // ========== course_evaluation_update.jsp ==========
    @PostMapping("/{id}/evaluation")
    public ResponseEntity<TutorApiResponse<Integer>> updateEvaluation(
            @PathVariable("id") int id,
            @RequestParam Map<String, String> params
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        boolean ok = courseService.updateEvaluation(auth, id, params);
        if (!ok) {
            return ResponseEntity.ok(TutorApiResponse.error(TutorApiResponse.CODE_NO_EDIT_PERMISSION, "수정 권한이 없습니다."));
        }
        return ResponseEntity.ok(TutorApiResponse.success(id));
    }

    // ========== course_feedback_report.jsp ==========
    @GetMapping("/{id}/feedback-report")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> feedbackReport(
            @PathVariable("id") int courseId,
            @RequestParam(name = "start_date", defaultValue = "") String startDate,
            @RequestParam(name = "end_date", defaultValue = "") String endDate
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        return courseService.getFeedbackReport(auth, courseId, startDate, endDate)
                .map(data -> ResponseEntity.ok(TutorApiResponse.success(data)))
                .orElse(ResponseEntity.ok(TutorApiResponse.error(TutorApiResponse.CODE_NO_EDIT_PERMISSION, "조회 권한이 없습니다.")));
    }

    // ========== course_image_upload.jsp ==========
    @PostMapping("/{id}/image")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> uploadImage(
            @PathVariable("id") int courseId,
            @RequestParam("course_file") MultipartFile file
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        Map<String, Object> result = courseService.uploadCourseImage(auth, courseId, file);
        return ResponseEntity.ok(TutorApiResponse.success(result));
    }

    // ========== course_list_combined.jsp ==========
    @GetMapping("/combined")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listCombined(
            @RequestParam(name = "tab", defaultValue = "prism") String tab,
            @RequestParam(name = "year", defaultValue = "") String year,
            @RequestParam(name = "s_keyword", defaultValue = "") String keyword,
            @RequestParam(name = "haksa_category", defaultValue = "") String haksaCategory,
            @RequestParam(name = "haksa_grad", defaultValue = "") String haksaGrad,
            @RequestParam(name = "haksa_curriculum", defaultValue = "") String haksaCurriculum,
            @RequestParam(name = "sort_order", defaultValue = "desc") String sortOrder,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        Map<String, Object> result = courseService.listCombined(auth, tab, year, keyword,
                haksaCategory, haksaGrad, haksaCurriculum, sortOrder, page, pageSize);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
        int count = data != null ? data.size() : 0;

        TutorApiResponse<List<Map<String, Object>>> response = new TutorApiResponse<>(
                TutorApiResponse.CODE_SUCCESS,
                (String) result.getOrDefault("message", "성공"),
                count,
                data
        );
        return ResponseEntity.ok(response);
    }

    // ========== course_list_poly.jsp ==========
    @GetMapping("/poly")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listPoly(
            @RequestParam(name = "year", defaultValue = "") String year,
            @RequestParam(name = "cnt", defaultValue = "1000") int cnt
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        List<Map<String, Object>> data = courseService.listPoly(auth, year, cnt);
        return ResponseEntity.ok(TutorApiResponse.success(data, data.size()));
    }

    // ========== course_resolve.jsp ==========
    @GetMapping("/resolve")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> resolveCourse(
            @RequestParam(name = "course_id") String courseId,
            @RequestParam(name = "source_type", defaultValue = "") String sourceType
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        return courseService.resolveCourse(auth, courseId, sourceType)
                .map(data -> ResponseEntity.ok(TutorApiResponse.success(data)))
                .orElse(ResponseEntity.ok(TutorApiResponse.notFound("과목")));
    }

    // ========== course_set_program.jsp ==========
    @PostMapping("/{id}/program")
    public ResponseEntity<TutorApiResponse<Integer>> setProgram(
            @PathVariable("id") int courseId,
            @RequestParam(name = "program_id") int programId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        boolean ok = courseService.setProgram(auth, courseId, programId);
        if (!ok) {
            return ResponseEntity.ok(TutorApiResponse.error(TutorApiResponse.CODE_NO_EDIT_PERMISSION, "수정 권한이 없습니다."));
        }
        return ResponseEntity.ok(TutorApiResponse.success(courseId));
    }

    // ========== course_students_list.jsp ==========
    @GetMapping("/{id}/students")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listStudents(
            @PathVariable("id") int courseId,
            @RequestParam(name = "s_keyword", defaultValue = "") String keyword
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        List<Map<String, Object>> students = courseService.listStudents(auth, courseId, keyword);
        return ResponseEntity.ok(TutorApiResponse.success(students, students.size()));
    }

    // ========== course_students_add.jsp ==========
    @PostMapping("/{id}/students")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> addStudents(
            @PathVariable("id") int courseId,
            @RequestParam(name = "user_ids") String userIds
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        Map<String, Object> result = courseService.addStudents(auth, courseId, userIds);
        return ResponseEntity.ok(TutorApiResponse.success(result));
    }

    // ========== course_students_remove.jsp ==========
    @PostMapping("/{id}/students/remove")
    public ResponseEntity<TutorApiResponse<Integer>> removeStudent(
            @PathVariable("id") int courseId,
            @RequestParam(name = "user_id") int userId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        boolean ok = courseService.removeStudent(auth, courseId, userId);
        if (!ok) {
            return ResponseEntity.ok(TutorApiResponse.error(TutorApiResponse.CODE_DB_FAIL, "수강 취소 처리에 실패했습니다."));
        }
        return ResponseEntity.ok(TutorApiResponse.success(userId));
    }
}
