package kr.go.growailms.tutor.curriculum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 왜: 레거시 DAO(CourseLessonDao, CourseSectionDao, LessonDao)가 하던 DB 접근을
 *     Spring Boot의 NamedParameterJdbcTemplate으로 대체합니다.
 *     테이블: LM_COURSE_LESSON, LM_COURSE_SECTION, LM_LESSON
 */
@Repository
public class TutorCurriculumJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(TutorCurriculumJdbcRepository.class);
    private final NamedParameterJdbcTemplate jdbc;

    public TutorCurriculumJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 권한 확인 ====================

    /**
     * 왜: 이 교수자가 해당 과목의 주담당인지 확인합니다.
     *     TutorCourseJdbcRepository.isMajorTutor와 동일 로직이나,
     *     패키지 독립성을 위해 별도로 두었습니다.
     */
    public boolean isMajorTutor(long userId, int courseId, long siteId) {
        String sql = """
                SELECT COUNT(*) FROM LM_COURSE_TUTOR
                WHERE course_id = :courseId
                  AND user_id = :userId
                  AND type = 'major'
                  AND site_id = :siteId
                  AND status != -1
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("userId", userId)
                .addValue("siteId", siteId);
        Integer count = jdbc.queryForObject(sql, params, Integer.class);
        return count != null && count > 0;
    }

    // ==================== 섹션 조회 ====================

    public List<Map<String, Object>> listSections(int courseId, long siteId) {
        String sql = """
                SELECT id, course_id, section_nm, sort, status, reg_date
                FROM LM_COURSE_SECTION
                WHERE course_id = :courseId AND site_id = :siteId AND status != -1
                ORDER BY sort ASC, id ASC
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("siteId", siteId));
    }

    // ==================== 레슨 조회 ====================

    /**
     * 왜: LM_COURSE_LESSON과 LM_LESSON을 JOIN하여 레슨 상세 정보를 함께 반환합니다.
     *     JSP에서 CourseLessonDao.listAll() + LessonDao.find()를 별도로 호출하던 것을
     *     한 번의 쿼리로 통합합니다.
     */
    public List<Map<String, Object>> listLessonsWithDetail(int courseId, long siteId) {
        String sql = """
                SELECT cl.id, cl.course_id, cl.lesson_id, cl.chapter, cl.section_id,
                       cl.complete_time, cl.start_date, cl.end_date, cl.status,
                       l.lesson_nm, l.lesson_type, l.content_type, l.content_url,
                       l.run_time, l.description
                FROM LM_COURSE_LESSON cl
                INNER JOIN LM_LESSON l ON l.id = cl.lesson_id AND l.site_id = :siteId AND l.status != -1
                WHERE cl.course_id = :courseId AND cl.site_id = :siteId AND cl.status != -1
                ORDER BY cl.section_id ASC, cl.chapter ASC, cl.id ASC
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("siteId", siteId));
    }

    // ==================== 레슨 추가/수정/삭제 ====================

    public boolean isLessonAlreadyAdded(int courseId, int lessonId, long siteId) {
        String sql = """
                SELECT COUNT(*) FROM LM_COURSE_LESSON
                WHERE course_id = :courseId AND lesson_id = :lessonId
                  AND site_id = :siteId AND status != -1
                """;
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("lessonId", lessonId)
                .addValue("siteId", siteId), Integer.class);
        return count != null && count > 0;
    }

    public long insertCourseLesson(int courseId, int lessonId, int sectionId, String chapter,
                                   int completeTime, String startDate, String endDate, long siteId) {
        String sql = """
                INSERT INTO LM_COURSE_LESSON (course_id, lesson_id, section_id, chapter,
                    complete_time, start_date, end_date, site_id, status, reg_date)
                VALUES (:courseId, :lessonId, :sectionId, :chapter,
                    :completeTime, :startDate, :endDate, :siteId, 1, NOW())
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("lessonId", lessonId)
                .addValue("sectionId", sectionId)
                .addValue("chapter", chapter)
                .addValue("completeTime", completeTime)
                .addValue("startDate", startDate)
                .addValue("endDate", endDate)
                .addValue("siteId", siteId), keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public int updateCourseLesson(int courseLessonId, int courseId, String chapter,
                                  int completeTime, String startDate, String endDate,
                                  int sectionId, long siteId) {
        String sql = """
                UPDATE LM_COURSE_LESSON SET
                    chapter = :chapter, complete_time = :completeTime,
                    start_date = :startDate, end_date = :endDate,
                    section_id = :sectionId, mod_date = NOW()
                WHERE id = :id AND course_id = :courseId AND site_id = :siteId AND status != -1
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", courseLessonId)
                .addValue("courseId", courseId)
                .addValue("chapter", chapter)
                .addValue("completeTime", completeTime)
                .addValue("startDate", startDate)
                .addValue("endDate", endDate)
                .addValue("sectionId", sectionId)
                .addValue("siteId", siteId));
    }

    public int softDeleteCourseLesson(int courseLessonId, int courseId, long siteId) {
        // 왜: 레슨 삭제는 하드 삭제가 아닌 status=-1로 소프트 삭제합니다.
        String sql = """
                UPDATE LM_COURSE_LESSON SET status = -1, mod_date = NOW()
                WHERE id = :id AND course_id = :courseId AND site_id = :siteId AND status != -1
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", courseLessonId)
                .addValue("courseId", courseId)
                .addValue("siteId", siteId));
    }

    // ==================== 섹션 추가/수정/삭제 ====================

    public int calcNextSectionSort(int courseId, long siteId) {
        String sql = """
                SELECT COUNT(*) FROM LM_COURSE_SECTION
                WHERE course_id = :courseId AND site_id = :siteId AND status != -1
                """;
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("siteId", siteId), Integer.class);
        return (count != null ? count : 0) + 1;
    }

    public long insertSection(int courseId, String sectionNm, int sort, long siteId) {
        String sql = """
                INSERT INTO LM_COURSE_SECTION (course_id, section_nm, sort, site_id, status, reg_date)
                VALUES (:courseId, :sectionNm, :sort, :siteId, 1, NOW())
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("sectionNm", sectionNm)
                .addValue("sort", sort)
                .addValue("siteId", siteId), keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public int updateSection(int sectionId, int courseId, String sectionNm, int sort, long siteId) {
        String sql = """
                UPDATE LM_COURSE_SECTION SET section_nm = :sectionNm, sort = :sort, mod_date = NOW()
                WHERE id = :id AND course_id = :courseId AND site_id = :siteId AND status != -1
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", sectionId)
                .addValue("courseId", courseId)
                .addValue("sectionNm", sectionNm)
                .addValue("sort", sort)
                .addValue("siteId", siteId));
    }

    public int softDeleteSection(int sectionId, int courseId, long siteId) {
        String sql = """
                UPDATE LM_COURSE_SECTION SET status = -1, mod_date = NOW()
                WHERE id = :id AND course_id = :courseId AND site_id = :siteId AND status != -1
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", sectionId)
                .addValue("courseId", courseId)
                .addValue("siteId", siteId));
    }

    /**
     * 왜: 섹션 삭제 시, 해당 섹션에 속한 레슨들을 미배정(section_id=0)으로 변경합니다.
     */
    public void unassignLessonsFromSection(int sectionId, int courseId, long siteId) {
        String sql = """
                UPDATE LM_COURSE_LESSON SET section_id = 0, mod_date = NOW()
                WHERE section_id = :sectionId AND course_id = :courseId
                  AND site_id = :siteId AND status != -1
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("sectionId", sectionId)
                .addValue("courseId", courseId)
                .addValue("siteId", siteId));
    }
}
