package kr.go.growailms.tutor.curriculum;

import kr.go.growailms.tutor.common.TutorApiResponse;
import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 왜: tutor_lms/api/curriculum_*.jsp 7개를 하나의 REST 컨트롤러로 통합합니다.
 *     init.jsp 인증은 TutorAuthInterceptor가 처리하므로, 여기서는 비즈니스 로직만 담당합니다.
 *
 * 엔드포인트 매핑:
 *   curriculum_list.jsp              → GET    /api/tutor/curriculum?course_id=
 *   curriculum_lesson_add.jsp        → POST   /api/tutor/curriculum/lessons
 *   curriculum_lesson_delete.jsp     → POST   /api/tutor/curriculum/lessons/delete
 *   curriculum_lesson_update.jsp     → POST   /api/tutor/curriculum/lessons/{id}
 *   curriculum_section_insert.jsp    → POST   /api/tutor/curriculum/sections
 *   curriculum_section_modify.jsp    → POST   /api/tutor/curriculum/sections/{id}
 *   curriculum_section_delete.jsp    → POST   /api/tutor/curriculum/sections/{id}/delete
 */
@RestController
@RequestMapping("/api/tutor/curriculum")
public class TutorCurriculumController {

    private static final Logger log = LoggerFactory.getLogger(TutorCurriculumController.class);

    private final TutorCurriculumService curriculumService;

    public TutorCurriculumController(TutorCurriculumService curriculumService) {
        this.curriculumService = curriculumService;
    }

    // ========== curriculum_list.jsp ==========
    @GetMapping
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> listCurriculum(
            @RequestParam(name = "course_id") int courseId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("커리큘럼 목록 조회: userId={}, courseId={}", auth.userId(), courseId);

        return curriculumService.getCurriculum(auth, courseId)
                .map(data -> ResponseEntity.ok(TutorApiResponse.success(data)))
                .orElse(ResponseEntity.ok(TutorApiResponse.error(
                        TutorApiResponse.CODE_NO_EDIT_PERMISSION, "커리큘럼 조회 권한이 없습니다.")));
    }

    // ========== curriculum_lesson_add.jsp ==========
    @PostMapping("/lessons")
    public ResponseEntity<TutorApiResponse<Long>> addLesson(
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "lesson_id") int lessonId,
            @RequestParam(name = "section_id", defaultValue = "0") int sectionId,
            @RequestParam(name = "chapter", defaultValue = "") String chapter,
            @RequestParam(name = "complete_time", defaultValue = "0") int completeTime,
            @RequestParam(name = "start_date", defaultValue = "") String startDate,
            @RequestParam(name = "end_date", defaultValue = "") String endDate
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("커리큘럼 레슨 추가: userId={}, courseId={}, lessonId={}", auth.userId(), courseId, lessonId);

        long newId = curriculumService.addLesson(auth, courseId, lessonId, sectionId,
                chapter, completeTime, startDate, endDate);
        if (newId <= 0) {
            return ResponseEntity.ok(TutorApiResponse.error(
                    TutorApiResponse.CODE_NO_EDIT_PERMISSION, "레슨 추가 권한이 없습니다."));
        }
        return ResponseEntity.ok(TutorApiResponse.success(newId));
    }

    // ========== curriculum_lesson_delete.jsp ==========
    @PostMapping("/lessons/delete")
    public ResponseEntity<TutorApiResponse<Integer>> deleteLesson(
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "id") int courseLessonId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("커리큘럼 레슨 삭제: userId={}, courseId={}, courseLessonId={}", auth.userId(), courseId, courseLessonId);

        boolean ok = curriculumService.deleteLesson(auth, courseId, courseLessonId);
        if (!ok) {
            return ResponseEntity.ok(TutorApiResponse.error(
                    TutorApiResponse.CODE_NO_EDIT_PERMISSION, "레슨 삭제 권한이 없습니다."));
        }
        return ResponseEntity.ok(TutorApiResponse.success(courseLessonId));
    }

    // ========== curriculum_lesson_update.jsp ==========
    @PostMapping("/lessons/{id}")
    public ResponseEntity<TutorApiResponse<Integer>> updateLesson(
            @PathVariable("id") int courseLessonId,
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "chapter", defaultValue = "") String chapter,
            @RequestParam(name = "complete_time", defaultValue = "0") int completeTime,
            @RequestParam(name = "start_date", defaultValue = "") String startDate,
            @RequestParam(name = "end_date", defaultValue = "") String endDate,
            @RequestParam(name = "section_id", defaultValue = "0") int sectionId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("커리큘럼 레슨 수정: userId={}, courseId={}, courseLessonId={}", auth.userId(), courseId, courseLessonId);

        boolean ok = curriculumService.updateLesson(auth, courseId, courseLessonId,
                chapter, completeTime, startDate, endDate, sectionId);
        if (!ok) {
            return ResponseEntity.ok(TutorApiResponse.error(
                    TutorApiResponse.CODE_NO_EDIT_PERMISSION, "레슨 수정 권한이 없습니다."));
        }
        return ResponseEntity.ok(TutorApiResponse.success(courseLessonId));
    }

    // ========== curriculum_section_insert.jsp ==========
    @PostMapping("/sections")
    public ResponseEntity<TutorApiResponse<Long>> insertSection(
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "section_nm") String sectionNm,
            @RequestParam(name = "sort", defaultValue = "0") int sort
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("커리큘럼 섹션 추가: userId={}, courseId={}, sectionNm={}", auth.userId(), courseId, sectionNm);

        long newId = curriculumService.insertSection(auth, courseId, sectionNm, sort);
        if (newId <= 0) {
            return ResponseEntity.ok(TutorApiResponse.error(
                    TutorApiResponse.CODE_NO_EDIT_PERMISSION, "섹션 추가 권한이 없습니다."));
        }
        return ResponseEntity.ok(TutorApiResponse.success(newId));
    }

    // ========== curriculum_section_modify.jsp ==========
    @PostMapping("/sections/{id}")
    public ResponseEntity<TutorApiResponse<Integer>> modifySection(
            @PathVariable("id") int sectionId,
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "section_nm") String sectionNm,
            @RequestParam(name = "sort", defaultValue = "0") int sort
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("커리큘럼 섹션 수정: userId={}, courseId={}, sectionId={}", auth.userId(), courseId, sectionId);

        boolean ok = curriculumService.modifySection(auth, courseId, sectionId, sectionNm, sort);
        if (!ok) {
            return ResponseEntity.ok(TutorApiResponse.error(
                    TutorApiResponse.CODE_NO_EDIT_PERMISSION, "섹션 수정 권한이 없습니다."));
        }
        return ResponseEntity.ok(TutorApiResponse.success(sectionId));
    }

    // ========== curriculum_section_delete.jsp ==========
    @PostMapping("/sections/{id}/delete")
    public ResponseEntity<TutorApiResponse<Integer>> deleteSection(
            @PathVariable("id") int sectionId,
            @RequestParam(name = "course_id") int courseId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("커리큘럼 섹션 삭제: userId={}, courseId={}, sectionId={}", auth.userId(), courseId, sectionId);

        boolean ok = curriculumService.deleteSection(auth, courseId, sectionId);
        if (!ok) {
            return ResponseEntity.ok(TutorApiResponse.error(
                    TutorApiResponse.CODE_NO_EDIT_PERMISSION, "섹션 삭제 권한이 없습니다."));
        }
        return ResponseEntity.ok(TutorApiResponse.success(sectionId));
    }
}
