package kr.go.growailms.tutor.progress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 왜: 레거시 DAO(CourseProgressDao, CourseLessonDao, CourseUserDao)가 하던 DB 접근을
 *     Spring Boot의 NamedParameterJdbcTemplate으로 대체합니다.
 *     테이블: LM_COURSE_PROGRESS, LM_COURSE_LESSON, LM_COURSE_USER
 */
@Repository
public class TutorProgressJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(TutorProgressJdbcRepository.class);
    private final NamedParameterJdbcTemplate jdbc;

    public TutorProgressJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 권한 확인 ====================

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

    // ==================== progress_summary.jsp ====================

    /**
     * 왜: 과목의 수강생 진도 통계를 한 번의 쿼리로 집계합니다.
     *     JSP에서 CourseUserDao.countAll() + 별도 루프 집계를 하던 것을 통합합니다.
     */
    public Map<String, Object> getProgressStats(int courseId, long siteId) {
        String sql = """
                SELECT
                    COUNT(*) AS total_students,
                    SUM(CASE WHEN complete_yn = 'Y' THEN 1 ELSE 0 END) AS completed_cnt,
                    SUM(CASE WHEN complete_yn != 'Y' AND progress_ratio > 0 THEN 1 ELSE 0 END) AS in_progress_cnt,
                    SUM(CASE WHEN progress_ratio = 0 OR progress_ratio IS NULL THEN 1 ELSE 0 END) AS not_started_cnt,
                    COALESCE(AVG(progress_ratio), 0) AS avg_progress
                FROM LM_COURSE_USER
                WHERE course_id = :courseId AND site_id = :siteId AND status NOT IN (-1, -4)
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("siteId", siteId)).stream()
                .findFirst()
                .orElse(Map.of("total_students", 0, "completed_cnt", 0,
                        "in_progress_cnt", 0, "not_started_cnt", 0, "avg_progress", 0));
    }

    /**
     * 왜: 레슨별 진도 통계(각 레슨의 완료율)를 조회합니다.
     */
    public List<Map<String, Object>> getLessonProgressStats(int courseId, long siteId) {
        String sql = """
                SELECT cl.id AS course_lesson_id, cl.lesson_id, cl.chapter,
                       l.lesson_nm,
                       (SELECT COUNT(*) FROM LM_COURSE_USER cu
                        WHERE cu.course_id = :courseId AND cu.site_id = :siteId AND cu.status NOT IN (-1, -4)) AS total_students,
                       (SELECT COUNT(*) FROM LM_COURSE_PROGRESS cp
                        WHERE cp.course_lesson_id = cl.id AND cp.site_id = :siteId
                          AND cp.complete_yn = 'Y' AND cp.status != -1) AS completed_students
                FROM LM_COURSE_LESSON cl
                INNER JOIN LM_LESSON l ON l.id = cl.lesson_id AND l.site_id = :siteId AND l.status != -1
                WHERE cl.course_id = :courseId AND cl.site_id = :siteId AND cl.status != -1
                ORDER BY cl.section_id ASC, cl.chapter ASC, cl.id ASC
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("siteId", siteId));
    }

    // ==================== progress_students.jsp ====================

    public List<Map<String, Object>> listProgressStudents(int courseId, long siteId,
                                                           String keyword, String completeStatus,
                                                           int page, int pageSize) {
        StringBuilder sql = new StringBuilder("""
                SELECT cu.id AS course_user_id, cu.user_id, cu.course_id,
                       cu.progress_ratio, cu.total_score, cu.complete_yn, cu.complete_status,
                       cu.complete_no, cu.start_date, cu.end_date, cu.reg_date,
                       u.login_id, u.user_nm, u.email
                FROM LM_COURSE_USER cu
                INNER JOIN TB_USER u ON u.id = cu.user_id AND u.site_id = :siteId
                WHERE cu.course_id = :courseId AND cu.site_id = :siteId
                  AND cu.status NOT IN (-1, -4)
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("siteId", siteId);

        if (!keyword.isEmpty()) {
            sql.append(" AND (u.user_nm LIKE :keyword OR u.login_id LIKE :keyword OR u.email LIKE :keyword) ");
            params.addValue("keyword", "%" + keyword + "%");
        }

        // 왜: 수료 상태 필터링
        if ("completed".equals(completeStatus)) {
            sql.append(" AND cu.complete_yn = 'Y' ");
        } else if ("incomplete".equals(completeStatus)) {
            sql.append(" AND (cu.complete_yn IS NULL OR cu.complete_yn != 'Y') ");
        } else if ("not_started".equals(completeStatus)) {
            sql.append(" AND (cu.progress_ratio = 0 OR cu.progress_ratio IS NULL) ");
        }

        sql.append(" ORDER BY u.login_id ASC ");

        // 왜: 페이지네이션 적용
        int offset = (page - 1) * pageSize;
        sql.append(" LIMIT :pageSize OFFSET :offset ");
        params.addValue("pageSize", pageSize);
        params.addValue("offset", offset);

        return jdbc.queryForList(sql.toString(), params);
    }

    // ==================== progress_detail.jsp ====================

    public Optional<Map<String, Object>> getCourseUser(int courseId, int userId, long siteId) {
        String sql = """
                SELECT cu.id AS course_user_id, cu.user_id, cu.course_id,
                       cu.progress_ratio, cu.total_score, cu.complete_yn, cu.complete_status,
                       cu.complete_no, cu.start_date, cu.end_date, cu.reg_date,
                       u.login_id, u.user_nm, u.email,
                       c.course_nm
                FROM LM_COURSE_USER cu
                INNER JOIN TB_USER u ON u.id = cu.user_id AND u.site_id = :siteId
                INNER JOIN LM_COURSE c ON c.id = cu.course_id AND c.site_id = :siteId
                WHERE cu.course_id = :courseId AND cu.user_id = :userId
                  AND cu.site_id = :siteId AND cu.status NOT IN (-1, -4)
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("userId", userId)
                .addValue("siteId", siteId)).stream().findFirst();
    }

    /**
     * 왜: 특정 수강생의 레슨별 진도 상세를 조회합니다.
     *     LM_COURSE_PROGRESS와 LM_COURSE_LESSON을 JOIN하여 한 번에 반환합니다.
     */
    public List<Map<String, Object>> getLessonProgress(int courseId, int userId, long siteId) {
        String sql = """
                SELECT cl.id AS course_lesson_id, cl.lesson_id, cl.chapter, cl.section_id,
                       l.lesson_nm, l.lesson_type, l.run_time,
                       cp.id AS progress_id, cp.progress_ratio, cp.complete_yn,
                       cp.last_page, cp.last_time, cp.study_time, cp.start_date, cp.end_date
                FROM LM_COURSE_LESSON cl
                INNER JOIN LM_LESSON l ON l.id = cl.lesson_id AND l.site_id = :siteId AND l.status != -1
                LEFT JOIN LM_COURSE_PROGRESS cp ON cp.course_lesson_id = cl.id
                    AND cp.user_id = :userId AND cp.site_id = :siteId AND cp.status != -1
                WHERE cl.course_id = :courseId AND cl.site_id = :siteId AND cl.status != -1
                ORDER BY cl.section_id ASC, cl.chapter ASC, cl.id ASC
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("userId", userId)
                .addValue("siteId", siteId));
    }
}
