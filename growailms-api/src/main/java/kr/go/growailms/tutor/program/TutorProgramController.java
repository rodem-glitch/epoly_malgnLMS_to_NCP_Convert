package kr.go.growailms.tutor.program;

import kr.go.growailms.tutor.common.TutorApiResponse;
import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 왜: tutor_lms/api/program_*.jsp 6개를 하나의 REST 컨트롤러로 통합합니다.
 *     init.jsp 인증은 TutorAuthInterceptor가 처리하므로, 여기서는 비즈니스 로직만 담당합니다.
 *
 * 엔드포인트 매핑:
 *   program_list.jsp        → GET    /api/tutor/programs
 *   program_view.jsp        → GET    /api/tutor/programs/{id}
 *   program_insert.jsp      → POST   /api/tutor/programs
 *   program_modify.jsp      → POST   /api/tutor/programs/{id}
 *   program_delete.jsp      → POST   /api/tutor/programs/{id}/delete
 *   program_course_list.jsp → GET    /api/tutor/programs/{id}/courses
 */
@RestController
@RequestMapping("/api/tutor/programs")
public class TutorProgramController {

    private static final Logger log = LoggerFactory.getLogger(TutorProgramController.class);

    private final TutorProgramService programService;

    public TutorProgramController(TutorProgramService programService) {
        this.programService = programService;
    }

    // ========== program_list.jsp ==========
    @GetMapping
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listPrograms(
            @RequestParam(name = "s_keyword", defaultValue = "") String keyword,
            @RequestParam(name = "year", defaultValue = "") String year
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("프로그램 목록 조회: userId={}, keyword={}, year={}", auth.userId(), keyword, year);

        List<Map<String, Object>> list = programService.listPrograms(auth, keyword, year);
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }

    // ========== program_view.jsp ==========
    @GetMapping("/{id}")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> viewProgram(
            @PathVariable("id") int id
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("프로그램 상세 조회: userId={}, programId={}", auth.userId(), id);

        return programService.getProgram(auth, id)
                .map(data -> ResponseEntity.ok(TutorApiResponse.success(data)))
                .orElse(ResponseEntity.ok(TutorApiResponse.notFound("프로그램")));
    }

    // ========== program_insert.jsp ==========
    @PostMapping
    public ResponseEntity<TutorApiResponse<Long>> insertProgram(
            @RequestParam(name = "course_nm") String courseNm,
            @RequestParam(name = "year", defaultValue = "") String year,
            @RequestParam(name = "start_date", defaultValue = "") String startDate,
            @RequestParam(name = "end_date", defaultValue = "") String endDate,
            @RequestParam(name = "description", defaultValue = "") String description,
            @RequestParam(name = "category_id", defaultValue = "0") int categoryId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("프로그램 생성: userId={}, courseNm={}", auth.userId(), courseNm);

        long newId = programService.insertProgram(auth, courseNm, year, startDate, endDate, description, categoryId);
        return ResponseEntity.ok(TutorApiResponse.success(newId));
    }

    // ========== program_modify.jsp ==========
    @PostMapping("/{id}")
    public ResponseEntity<TutorApiResponse<Integer>> modifyProgram(
            @PathVariable("id") int id,
            @RequestParam(name = "course_nm") String courseNm,
            @RequestParam(name = "year", defaultValue = "") String year,
            @RequestParam(name = "start_date", defaultValue = "") String startDate,
            @RequestParam(name = "end_date", defaultValue = "") String endDate,
            @RequestParam(name = "description", defaultValue = "") String description,
            @RequestParam(name = "category_id", defaultValue = "0") int categoryId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("프로그램 수정: userId={}, programId={}", auth.userId(), id);

        boolean ok = programService.modifyProgram(auth, id, courseNm, year, startDate, endDate, description, categoryId);
        if (!ok) {
            return ResponseEntity.ok(TutorApiResponse.error(
                    TutorApiResponse.CODE_NO_EDIT_PERMISSION, "프로그램 수정 권한이 없습니다."));
        }
        return ResponseEntity.ok(TutorApiResponse.success(id));
    }

    // ========== program_delete.jsp ==========
    @PostMapping("/{id}/delete")
    public ResponseEntity<TutorApiResponse<Integer>> deleteProgram(
            @PathVariable("id") int id
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("프로그램 삭제: userId={}, programId={}", auth.userId(), id);

        boolean ok = programService.deleteProgram(auth, id);
        if (!ok) {
            return ResponseEntity.ok(TutorApiResponse.error(
                    TutorApiResponse.CODE_NO_EDIT_PERMISSION, "프로그램 삭제 권한이 없습니다."));
        }
        return ResponseEntity.ok(TutorApiResponse.success(id));
    }

    // ========== program_course_list.jsp ==========
    @GetMapping("/{id}/courses")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listProgramCourses(
            @PathVariable("id") int programId,
            @RequestParam(name = "year", defaultValue = "") String year
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("프로그램 과목 목록: userId={}, programId={}", auth.userId(), programId);

        List<Map<String, Object>> courses = programService.listProgramCourses(auth, programId, year);
        return ResponseEntity.ok(TutorApiResponse.success(courses, courses.size()));
    }
}
