package kr.go.growailms.tutor.content;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 왜: content_recommend.jsp가 외부 API 프록시로 하던 콘텐츠 검색의 DB fallback을 담당합니다.
 *     키워드 기반으로 LM_LESSON 테이블에서 콘텐츠를 검색합니다.
 *     추천 엔진 연동이 완료되면 이 fallback은 보조 수단으로 유지됩니다.
 */
@Repository
public class TutorContentJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(TutorContentJdbcRepository.class);
    private final NamedParameterJdbcTemplate jdbc;

    public TutorContentJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 키워드 기반 콘텐츠 검색 ====================

    /**
     * 왜: 추천 엔진이 없을 때 fallback으로 키워드 기반 검색을 수행합니다.
     */
    public List<Map<String, Object>> searchByKeyword(long siteId, String keywords, int topK) {
        // 왜: 키워드를 공백 또는 콤마로 분리하여 OR 검색
        String[] tokens = keywords.split("[,\\s]+");
        StringBuilder whereClause = new StringBuilder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", siteId)
                .addValue("topK", topK);

        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].trim().isEmpty()) continue;
            if (whereClause.length() > 0) {
                whereClause.append(" OR ");
            }
            String paramName = "kw" + i;
            whereClause.append("l.lesson_nm LIKE :").append(paramName);
            params.addValue(paramName, "%" + tokens[i].trim() + "%");
        }

        if (whereClause.length() == 0) {
            // 왜: 키워드가 비어있으면 빈 결과 반환
            return List.of();
        }

        String sql = """
                SELECT l.id lesson_id, l.lesson_nm, l.lesson_type, l.total_time,
                       l.media_content_key, l.content_width, l.content_height
                FROM LM_LESSON l
                WHERE l.site_id = :siteId AND l.status != -1
                  AND (""" + whereClause + """
                )
                ORDER BY l.id DESC
                LIMIT :topK
                """;

        return jdbc.queryForList(sql, params);
    }
}
