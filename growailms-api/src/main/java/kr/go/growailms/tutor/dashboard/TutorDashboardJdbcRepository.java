package kr.go.growailms.tutor.dashboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 왜: 레거시 dashboard.jsp에서 여러 DAO를 호출하던 DB 접근을
 *     Spring Boot의 NamedParameterJdbcTemplate으로 대체합니다.
 *     테이블: LM_COURSE, LM_COURSE_USER, LM_HOMEWORK_USER, CL_POST, CL_BOARD
 */
@Repository
public class TutorDashboardJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(TutorDashboardJdbcRepository.class);
    private final NamedParameterJdbcTemplate jdbc;

    public TutorDashboardJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 통계 카드 ====================

    /**
     * 왜: 교수자가 담당하는 활성 과목 수를 집계합니다.
     */
    public int countCourses(long userId, long siteId, boolean isAdmin) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) FROM LM_COURSE c
                """);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("siteId", siteId);

        if (!isAdmin) {
            sql.append("""
                    INNER JOIN LM_COURSE_TUTOR ct ON ct.course_id = c.id
                        AND ct.user_id = :userId AND ct.type = 'major'
                        AND ct.site_id = :siteId AND ct.status != -1
                    """);
            params.addValue("userId", userId);
        }
        sql.append(" WHERE c.site_id = :siteId AND c.status != -1 ");

        Integer count = jdbc.queryForObject(sql.toString(), params, Integer.class);
        return count != null ? count : 0;
    }

    /**
     * 왜: 교수자 담당 과목의 전체 수강생 수를 집계합니다.
     */
    public int countStudents(long userId, long siteId, boolean isAdmin) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(DISTINCT cu.user_id) FROM LM_COURSE_USER cu
                INNER JOIN LM_COURSE c ON c.id = cu.course_id AND c.site_id = :siteId AND c.status != -1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("siteId", siteId);

        if (!isAdmin) {
            sql.append("""
                    INNER JOIN LM_COURSE_TUTOR ct ON ct.course_id = c.id
                        AND ct.user_id = :userId AND ct.type = 'major'
                        AND ct.site_id = :siteId AND ct.status != -1
                    """);
            params.addValue("userId", userId);
        }
        sql.append(" WHERE cu.site_id = :siteId AND cu.status NOT IN (-1, -4) ");

        Integer count = jdbc.queryForObject(sql.toString(), params, Integer.class);
        return count != null ? count : 0;
    }

    /**
     * 왜: 미답변 QnA 수를 집계합니다.
     */
    public int countUnansweredQna(long userId, long siteId, boolean isAdmin) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) FROM CL_POST p
                INNER JOIN CL_BOARD b ON b.id = p.board_id AND b.site_id = :siteId
                    AND b.board_type = 'qna' AND b.status != -1
                INNER JOIN LM_COURSE c ON c.id = b.course_id AND c.site_id = :siteId AND c.status != -1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("siteId", siteId);

        if (!isAdmin) {
            sql.append("""
                    INNER JOIN LM_COURSE_TUTOR ct ON ct.course_id = c.id
                        AND ct.user_id = :userId AND ct.type = 'major'
                        AND ct.site_id = :siteId AND ct.status != -1
                    """);
            params.addValue("userId", userId);
        }
        sql.append("""
                WHERE p.site_id = :siteId AND p.status != -1
                  AND (p.parent_id IS NULL OR p.parent_id = 0)
                  AND (p.answer_content IS NULL OR p.answer_content = '')
                """);

        Integer count = jdbc.queryForObject(sql.toString(), params, Integer.class);
        return count != null ? count : 0;
    }

    /**
     * 왜: 최근 7일간 과제 제출 수를 집계합니다.
     */
    public int countRecentSubmissions(long userId, long siteId, boolean isAdmin) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) FROM LM_HOMEWORK_USER hu
                INNER JOIN LM_COURSE c ON c.id = hu.course_id AND c.site_id = :siteId AND c.status != -1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("siteId", siteId);

        if (!isAdmin) {
            sql.append("""
                    INNER JOIN LM_COURSE_TUTOR ct ON ct.course_id = c.id
                        AND ct.user_id = :userId AND ct.type = 'major'
                        AND ct.site_id = :siteId AND ct.status != -1
                    """);
            params.addValue("userId", userId);
        }
        sql.append("""
                WHERE hu.site_id = :siteId AND hu.status != -1
                  AND hu.reg_date >= DATE_SUB(NOW(), INTERVAL 7 DAY)
                """);

        Integer count = jdbc.queryForObject(sql.toString(), params, Integer.class);
        return count != null ? count : 0;
    }

    // ==================== 활성 과목 Top N ====================

    /**
     * 왜: 수강생 수 기준 상위 N개의 활성 과목을 조회합니다.
     */
    public List<Map<String, Object>> listTopActiveCourses(long userId, long siteId,
                                                           boolean isAdmin, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.course_nm, c.year, c.study_sdate, c.study_edate,
                       (SELECT COUNT(*) FROM LM_COURSE_USER cu
                        WHERE cu.course_id = c.id AND cu.site_id = :siteId AND cu.status NOT IN (-1, -4)) AS student_cnt
                FROM LM_COURSE c
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", siteId)
                .addValue("limit", limit);

        if (!isAdmin) {
            sql.append("""
                    INNER JOIN LM_COURSE_TUTOR ct ON ct.course_id = c.id
                        AND ct.user_id = :userId AND ct.type = 'major'
                        AND ct.site_id = :siteId AND ct.status != -1
                    """);
            params.addValue("userId", userId);
        }
        sql.append("""
                WHERE c.site_id = :siteId AND c.status != -1
                ORDER BY student_cnt DESC, c.id DESC
                LIMIT :limit
                """);

        return jdbc.queryForList(sql.toString(), params);
    }

    // ==================== 최근 과제 제출물 ====================

    public List<Map<String, Object>> listRecentSubmissions(long userId, long siteId,
                                                            boolean isAdmin, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT hu.id, hu.course_id, hu.user_id, hu.homework_id,
                       hu.content, hu.score, hu.reg_date,
                       c.course_nm,
                       u.user_nm, u.login_id
                FROM LM_HOMEWORK_USER hu
                INNER JOIN LM_COURSE c ON c.id = hu.course_id AND c.site_id = :siteId AND c.status != -1
                LEFT JOIN TB_USER u ON u.id = hu.user_id AND u.site_id = :siteId
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", siteId)
                .addValue("limit", limit);

        if (!isAdmin) {
            sql.append("""
                    INNER JOIN LM_COURSE_TUTOR ct ON ct.course_id = c.id
                        AND ct.user_id = :userId AND ct.type = 'major'
                        AND ct.site_id = :siteId AND ct.status != -1
                    """);
            params.addValue("userId", userId);
        }
        sql.append("""
                WHERE hu.site_id = :siteId AND hu.status != -1
                ORDER BY hu.reg_date DESC
                LIMIT :limit
                """);

        return jdbc.queryForList(sql.toString(), params);
    }

    // ==================== 최근 QnA ====================

    public List<Map<String, Object>> listRecentQna(long userId, long siteId,
                                                    boolean isAdmin, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.title, p.user_id, p.answer_content, p.reg_date,
                       b.course_id,
                       c.course_nm,
                       u.user_nm, u.login_id
                FROM CL_POST p
                INNER JOIN CL_BOARD b ON b.id = p.board_id AND b.site_id = :siteId
                    AND b.board_type = 'qna' AND b.status != -1
                INNER JOIN LM_COURSE c ON c.id = b.course_id AND c.site_id = :siteId AND c.status != -1
                LEFT JOIN TB_USER u ON u.id = p.user_id AND u.site_id = :siteId
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", siteId)
                .addValue("limit", limit);

        if (!isAdmin) {
            sql.append("""
                    INNER JOIN LM_COURSE_TUTOR ct ON ct.course_id = c.id
                        AND ct.user_id = :userId AND ct.type = 'major'
                        AND ct.site_id = :siteId AND ct.status != -1
                    """);
            params.addValue("userId", userId);
        }
        sql.append("""
                WHERE p.site_id = :siteId AND p.status != -1
                  AND (p.parent_id IS NULL OR p.parent_id = 0)
                ORDER BY p.reg_date DESC
                LIMIT :limit
                """);

        return jdbc.queryForList(sql.toString(), params);
    }
}
