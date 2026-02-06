package kr.go.growailms.tutor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 왜: init.jsp가 UserDao.find("id = ? AND site_id = ? AND status = 1")로 하던 인증 조회를
 *     Spring Boot JdbcTemplate으로 대체합니다.
 *     TB_USER에서 활성(status=1) 사용자의 교수자 여부(tutor_yn)와 권한(user_kind)을 확인합니다.
 */
@Repository
public class TutorUserJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(TutorUserJdbcRepository.class);

    private final NamedParameterJdbcTemplate jdbc;

    public TutorUserJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 왜: init.jsp의 "사용자 존재 + 활성 확인 + 교수자 여부 확인"을 한 번의 쿼리로 수행합니다.
     */
    public Optional<TutorUserInfo> findActiveUser(long userId, long siteId) {
        String sql = """
                SELECT id, site_id, user_kind, tutor_yn
                FROM TB_USER
                WHERE id = :userId
                  AND site_id = :siteId
                  AND status = 1
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("siteId", siteId);

        try {
            return jdbc.query(sql, params, (rs, rowNum) -> new TutorUserInfo(
                    rs.getLong("id"),
                    rs.getLong("site_id"),
                    rs.getString("user_kind"),
                    "Y".equalsIgnoreCase(rs.getString("tutor_yn"))
            )).stream().findFirst();
        } catch (Exception ex) {
            log.error("교수자 인증 조회 실패: userId={}, siteId={}, error={}",
                    userId, siteId, ex.getMessage());
            return Optional.empty();
        }
    }

    public record TutorUserInfo(
            long userId,
            long siteId,
            String userKind,
            boolean isTutor
    ) {
    }
}
