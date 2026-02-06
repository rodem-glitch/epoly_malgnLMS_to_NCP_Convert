package kr.go.growailms.tutor.completion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 왜: completion_list.jsp, completion_update.jsp가 CourseUserDao, CourseDao 등으로 하던 DB 접근을
 *     NamedParameterJdbcTemplate으로 대체합니다.
 *     테이블: LM_COURSE_USER, LM_COURSE, LM_COURSE_TUTOR, TB_USER
 */
@Repository
public class TutorCompletionJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(TutorCompletionJdbcRepository.class);
    private final NamedParameterJdbcTemplate jdbc;

    public TutorCompletionJdbcRepository(NamedParameterJdbcTemplate jdbc) {
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

    // ==================== completion_list.jsp ====================

    /**
     * 왜: 수료 관리 목록 — 수강생별 진도율, 점수, 수료 상태를 조회합니다.
     */
    public List<Map<String, Object>> listCompletions(int courseId, long siteId,
                                                      String keyword, String completeStatus) {
        StringBuilder sql = new StringBuilder("""
                SELECT cu.id course_user_id, cu.user_id,
                       u.login_id, u.user_nm,
                       cu.start_date, cu.end_date, cu.progress_ratio, cu.total_score,
                       cu.complete_status, cu.close_yn, cu.complete_date
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
        if (completeStatus != null && !completeStatus.isEmpty()) {
            sql.append(" AND cu.complete_status = :completeStatus ");
            params.addValue("completeStatus", completeStatus);
        }
        sql.append(" ORDER BY u.user_nm ");

        return jdbc.queryForList(sql.toString(), params);
    }

    // ==================== completion_update.jsp ====================

    /**
     * 왜: 수료/합격/마감 상태를 일괄 변경합니다.
     *     action: complete_y, complete_n, close_y, close_n
     */
    public int updateCompletion(int courseId, long siteId, String action, String courseUserIds) {
        List<Long> ids = Arrays.stream(courseUserIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());

        // 왜: action에 따라 update 컬럼과 값이 달라집니다.
        String setClause;
        switch (action) {
            case "complete_y" -> setClause = "complete_status = 'Y', complete_date = NOW()";
            case "complete_n" -> setClause = "complete_status = 'N', complete_date = NULL";
            case "close_y"    -> setClause = "close_yn = 'Y'";
            case "close_n"    -> setClause = "close_yn = 'N'";
            default -> {
                log.warn("알 수 없는 action: {}", action);
                return 0;
            }
        }

        String sql = "UPDATE LM_COURSE_USER SET " + setClause
                + " WHERE id IN (:ids) AND course_id = :courseId AND site_id = :siteId";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids", ids)
                .addValue("courseId", courseId)
                .addValue("siteId", siteId);

        return jdbc.update(sql, params);
    }
}
