package kr.go.growailms.tutor.certificate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 왜: certificate_templates.jsp, certificate_issue.jsp가 CertificateTemplateDao, CourseUserDao 등으로
 *     하던 DB 접근을 NamedParameterJdbcTemplate으로 대체합니다.
 *     테이블: LM_CERTIFICATE_TEMPLATE, LM_COURSE_USER, LM_COURSE_TUTOR
 */
@Repository
public class TutorCertificateJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(TutorCertificateJdbcRepository.class);
    private final NamedParameterJdbcTemplate jdbc;

    public TutorCertificateJdbcRepository(NamedParameterJdbcTemplate jdbc) {
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

    // ==================== certificate_templates.jsp ====================

    /**
     * 왜: 증명서 템플릿 목록을 조회합니다. template_type 필터는 선택 사항입니다.
     */
    public List<Map<String, Object>> listTemplates(long siteId, String templateType) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, template_nm, template_type, content, reg_date
                FROM LM_CERTIFICATE_TEMPLATE
                WHERE site_id = :siteId AND status != -1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", siteId);

        if (templateType != null && !templateType.isEmpty()) {
            sql.append(" AND template_type = :templateType ");
            params.addValue("templateType", templateType);
        }
        sql.append(" ORDER BY id DESC ");

        return jdbc.queryForList(sql.toString(), params);
    }

    // ==================== certificate_issue.jsp ====================

    /**
     * 왜: 수강 정보(course_user)를 조회하여 증명서 발급 대상인지 확인합니다.
     */
    public Optional<Map<String, Object>> findCourseUser(int courseUserId, long siteId) {
        String sql = """
                SELECT id, course_id, user_id, complete_status, close_yn
                FROM LM_COURSE_USER
                WHERE id = :courseUserId AND site_id = :siteId AND status IN (1, 3)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseUserId", courseUserId)
                .addValue("siteId", siteId);

        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
