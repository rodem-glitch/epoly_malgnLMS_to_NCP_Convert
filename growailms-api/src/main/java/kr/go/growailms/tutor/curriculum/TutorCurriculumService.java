package kr.go.growailms.tutor.curriculum;

import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 왜: curriculum_*.jsp 7개의 비즈니스 로직을 모아둔 서비스입니다.
 *     각 JSP에서 init.jsp 인증 이후 수행하던 로직(권한 체크, 데이터 가공, DB 호출)을 담당합니다.
 *     커리큘럼 = 섹션(LM_COURSE_SECTION) + 레슨(LM_COURSE_LESSON → LM_LESSON) 트리 구조입니다.
 */
@Service
public class TutorCurriculumService {

    private static final Logger log = LoggerFactory.getLogger(TutorCurriculumService.class);

    private final TutorCurriculumJdbcRepository repo;

    public TutorCurriculumService(TutorCurriculumJdbcRepository repo) {
        this.repo = repo;
    }

    // ========== 권한 확인 공통 메서드 ==========

    private boolean canAccessCourse(TutorAuthContext.AuthInfo auth, int courseId) {
        // 왜: 관리자는 전체 과목, 교수자는 주담당 과목만 접근 가능합니다.
        return auth.isAdmin() || repo.isMajorTutor(auth.userId(), courseId, auth.siteId());
    }

    // ========== curriculum_list.jsp ==========

    public Optional<Map<String, Object>> getCurriculum(TutorAuthContext.AuthInfo auth, int courseId) {
        if (!canAccessCourse(auth, courseId)) {
            return Optional.empty();
        }

        // 왜: 커리큘럼은 섹션 목록 + 레슨 목록을 합쳐서 트리 구조로 반환합니다.
        List<Map<String, Object>> sections = repo.listSections(courseId, auth.siteId());
        List<Map<String, Object>> lessons = repo.listLessonsWithDetail(courseId, auth.siteId());

        // 왜: JSP에서 하던 트리 구성 로직을 재현합니다. 섹션별로 레슨을 그룹핑합니다.
        Map<Object, List<Map<String, Object>>> lessonsBySection = new LinkedHashMap<>();
        for (Map<String, Object> lesson : lessons) {
            Object sectionId = lesson.get("section_id");
            lessonsBySection.computeIfAbsent(sectionId, k -> new ArrayList<>()).add(lesson);
        }

        for (Map<String, Object> section : sections) {
            Object sectionId = section.get("id");
            section.put("lessons", lessonsBySection.getOrDefault(sectionId, List.of()));
        }

        // 왜: 섹션에 속하지 않는 레슨(section_id=0 또는 null)도 포함합니다.
        List<Map<String, Object>> unassigned = new ArrayList<>();
        unassigned.addAll(lessonsBySection.getOrDefault(0, List.of()));
        unassigned.addAll(lessonsBySection.getOrDefault(null, List.of()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("course_id", courseId);
        result.put("sections", sections);
        result.put("unassigned_lessons", unassigned);
        result.put("total_lessons", lessons.size());

        return Optional.of(result);
    }

    // ========== curriculum_lesson_add.jsp ==========

    public long addLesson(TutorAuthContext.AuthInfo auth, int courseId, int lessonId,
                          int sectionId, String chapter, int completeTime,
                          String startDate, String endDate) {
        if (!canAccessCourse(auth, courseId)) {
            return -1;
        }

        // 왜: 동일 과목에 같은 레슨이 이미 등록되어 있는지 확인합니다.
        if (repo.isLessonAlreadyAdded(courseId, lessonId, auth.siteId())) {
            log.warn("이미 등록된 레슨: courseId={}, lessonId={}", courseId, lessonId);
            return -1;
        }

        long newId = repo.insertCourseLesson(courseId, lessonId, sectionId, chapter,
                completeTime, startDate, endDate, auth.siteId());
        log.info("커리큘럼 레슨 추가: courseId={}, lessonId={}, newCourseLessonId={}", courseId, lessonId, newId);
        return newId;
    }

    // ========== curriculum_lesson_delete.jsp ==========

    public boolean deleteLesson(TutorAuthContext.AuthInfo auth, int courseId, int courseLessonId) {
        if (!canAccessCourse(auth, courseId)) {
            return false;
        }
        // 왜: 레슨 삭제는 소프트 삭제(status=-1)로 처리합니다.
        int updated = repo.softDeleteCourseLesson(courseLessonId, courseId, auth.siteId());
        if (updated > 0) {
            log.info("커리큘럼 레슨 삭제: courseLessonId={}, courseId={}", courseLessonId, courseId);
        }
        return updated > 0;
    }

    // ========== curriculum_lesson_update.jsp ==========

    public boolean updateLesson(TutorAuthContext.AuthInfo auth, int courseId, int courseLessonId,
                                String chapter, int completeTime, String startDate,
                                String endDate, int sectionId) {
        if (!canAccessCourse(auth, courseId)) {
            return false;
        }
        int updated = repo.updateCourseLesson(courseLessonId, courseId, chapter,
                completeTime, startDate, endDate, sectionId, auth.siteId());
        if (updated > 0) {
            log.info("커리큘럼 레슨 수정: courseLessonId={}, courseId={}", courseLessonId, courseId);
        }
        return updated > 0;
    }

    // ========== curriculum_section_insert.jsp ==========

    public long insertSection(TutorAuthContext.AuthInfo auth, int courseId, String sectionNm, int sort) {
        if (!canAccessCourse(auth, courseId)) {
            return -1;
        }
        // 왜: sort가 0이면 기존 섹션 수 + 1로 자동 계산합니다.
        if (sort <= 0) {
            sort = repo.calcNextSectionSort(courseId, auth.siteId());
        }
        long newId = repo.insertSection(courseId, sectionNm, sort, auth.siteId());
        log.info("커리큘럼 섹션 추가: courseId={}, sectionNm={}, newSectionId={}", courseId, sectionNm, newId);
        return newId;
    }

    // ========== curriculum_section_modify.jsp ==========

    public boolean modifySection(TutorAuthContext.AuthInfo auth, int courseId, int sectionId,
                                 String sectionNm, int sort) {
        if (!canAccessCourse(auth, courseId)) {
            return false;
        }
        int updated = repo.updateSection(sectionId, courseId, sectionNm, sort, auth.siteId());
        if (updated > 0) {
            log.info("커리큘럼 섹션 수정: sectionId={}, courseId={}", sectionId, courseId);
        }
        return updated > 0;
    }

    // ========== curriculum_section_delete.jsp ==========

    public boolean deleteSection(TutorAuthContext.AuthInfo auth, int courseId, int sectionId) {
        if (!canAccessCourse(auth, courseId)) {
            return false;
        }
        // 왜: 섹션 삭제 시, 해당 섹션에 속한 레슨은 section_id=0으로 변경하여 미배정 상태로 전환합니다.
        repo.unassignLessonsFromSection(sectionId, courseId, auth.siteId());
        int updated = repo.softDeleteSection(sectionId, courseId, auth.siteId());
        if (updated > 0) {
            log.info("커리큘럼 섹션 삭제: sectionId={}, courseId={}", sectionId, courseId);
        }
        return updated > 0;
    }
}
