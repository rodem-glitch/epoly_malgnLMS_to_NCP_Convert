package kr.go.growailms.tutor.learner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 왜: learner_list.jsp가 UserDao, UserDeptDao로 하던 DB 접근을
 *     NamedParameterJdbcTemplate으로 대체합니다.
 *     테이블: TB_USER, TB_USER_DEPT
 */
@Repository
public class TutorLearnerJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(TutorLearnerJdbcRepository.class);
    private final NamedParameterJdbcTemplate jdbc;

    public TutorLearnerJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== learner_list.jsp ====================

    /**
     * 왜: 회원 검색 — 이름/아이디/이메일로 검색, 부서 필터 지원, 페이징 지원.
     *     user_kind = 'U'(학습자)만 대상.
     */
    public List<Map<String, Object>> listLearners(long siteId, String keyword, String deptKeyword,
                                                    int deptId, int offset, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT u.id user_id, u.login_id, u.user_nm, u.email, u.dept_id, u.reg_date
                FROM TB_USER u
                WHERE u.site_id = :siteId AND u.status = 1 AND u.user_kind = 'U'
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", siteId)
                .addValue("offset", offset)
                .addValue("limit", limit);

        // 왜: 이름/아이디/이메일 중 하나라도 일치하면 검색 결과에 포함
        if (keyword != null && !keyword.isEmpty()) {
            sql.append(" AND (u.user_nm LIKE :keyword OR u.login_id LIKE :keyword OR u.email LIKE :keyword) ");
            params.addValue("keyword", "%" + keyword + "%");
        }

        // 왜: 부서 ID 필터
        if (deptId > 0) {
            sql.append(" AND u.dept_id = :deptId ");
            params.addValue("deptId", deptId);
        } else if (deptKeyword != null && !deptKeyword.isEmpty()) {
            // 왜: 부서명 필터는 EXISTS 서브쿼리로 — LEFT JOIN 실패 시 전체 조회가 깨지는 것을 방지
            sql.append("""
                    AND EXISTS (
                        SELECT 1 FROM TB_USER_DEPT d
                        WHERE d.id = u.dept_id AND d.site_id = :siteId AND d.dept_nm LIKE :deptKeyword
                    )
                    """);
            params.addValue("deptKeyword", "%" + deptKeyword + "%");
        }

        sql.append(" ORDER BY u.user_nm, u.login_id ");
        sql.append(" LIMIT :limit OFFSET :offset ");

        return jdbc.queryForList(sql.toString(), params);
    }
}
