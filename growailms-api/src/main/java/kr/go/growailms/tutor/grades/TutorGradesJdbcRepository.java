package kr.go.growailms.tutor.grades;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 왜: grades_list.jsp, grades_recalc.jsp가 CourseUserDao, CourseDao 등으로 하던 DB 접근을
 *     NamedParameterJdbcTemplate으로 대체합니다.
 *     테이블: LM_COURSE_USER, LM_COURSE, LM_COURSE_TUTOR, TB_USER
 */
@Repository
public class TutorGradesJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(TutorGradesJdbcRepository.class);
    private final NamedParameterJdbcTemplate jdbc;

    public TutorGradesJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 권한 확인 ====================

    public boolean isMajorTutor(long userId, int courseId, long siteId) {
        String sql = """
                SELECT COUNT(*) FROM LM_COURSE_TUTOR
                WHERE course_id = :courseId AND user_id = :userId
                  AND type = 'major' AND site_id = :siteId AND status != -1
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("userId", userId)
                .addValue("siteId", siteId);
        Integer count = jdbc.queryForObject(sql, params, Integer.class);
        return count != null && count > 0;
    }

    // ==================== grades_list.jsp ====================

    /**
     * 왜: 성적 관리 목록 — 수강생별 진도점수, 시험점수, 과제점수, 총점을 조회합니다.
     */
    public List<Map<String, Object>> listGrades(int courseId, long siteId, String keyword) {
        StringBuilder sql = new StringBuilder("""
                SELECT cu.id course_user_id, cu.user_id,
                       u.login_id, u.user_nm,
                       cu.progress_ratio, cu.progress_score,
                       cu.exam_score, cu.homework_score, cu.total_score,
                       cu.complete_status
                FROM LM_COURSE_USER cu
                INNER JOIN TB_USER u ON cu.user_id = u.id AND u.site_id = :siteId
                WHERE cu.course_id = :courseId AND cu.site_id = :siteId AND cu.status IN (1, 3)
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("siteId", siteId);

        if (keyword != null && !keyword.isEmpty()) {
            sql.append(" AND (u.user_nm LIKE :keyword OR u.login_id LIKE :keyword) ");
            params.addValue("keyword", "%" + keyword + "%");
        }
        sql.append(" ORDER BY u.user_nm ");

        return jdbc.queryForList(sql.toString(), params);
    }

    // ==================== grades_recalc.jsp ====================

    /**
     * 왜: 총점/수료기준을 재계산합니다. CourseUserDao.updateUserScore 로직을 재현합니다.
     *     courseUserId = 0이면 과목 전체, 아니면 특정 수강생만 대상.
     *     TODO: 실제 재계산 로직(진도/시험/과제 비율 반영)은 레거시 로직에 맞춰 보완 필요
     */
    public int recalcGrades(int courseId, long siteId, int courseUserId) {
        // 왜: 간이 재계산 — progress_score + exam_score + homework_score = total_score
        StringBuilder sql = new StringBuilder("""
                UPDATE LM_COURSE_USER
                SET total_score = COALESCE(progress_score, 0) + COALESCE(exam_score, 0) + COALESCE(homework_score, 0)
                WHERE course_id = :courseId AND site_id = :siteId AND status IN (1, 3)
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("siteId", siteId);

        if (courseUserId > 0) {
            sql.append(" AND id = :courseUserId ");
            params.addValue("courseUserId", courseUserId);
        }

        return jdbc.update(sql.toString(), params);
    }
}
