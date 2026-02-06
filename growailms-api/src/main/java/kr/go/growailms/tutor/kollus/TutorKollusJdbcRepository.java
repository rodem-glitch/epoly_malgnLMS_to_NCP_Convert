package kr.go.growailms.tutor.kollus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 왜: kollus_*.jsp 7개가 KollusDao, KollusMediaDao, WishlistDao, LessonDao 등으로 하던 DB 접근을
 *     NamedParameterJdbcTemplate으로 대체합니다.
 *     외부 Kollus API 호출은 서비스 레이어에서 처리하고, 여기서는 DB 접근만 담당합니다.
 *     테이블: LM_LESSON, LM_KOLLUS_WISHLIST, TB_SITE
 */
@Repository
public class TutorKollusJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(TutorKollusJdbcRepository.class);
    private final NamedParameterJdbcTemplate jdbc;

    public TutorKollusJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 사이트 설정 ====================

    /**
     * 왜: 멀티사이트 환경에서 사이트별 Kollus access_token을 DB에서 읽습니다.
     */
    public String getKollusAccessToken(long siteId) {
        String sql = "SELECT access_token FROM TB_SITE WHERE id = :siteId";
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("siteId", siteId);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        if (rows.isEmpty()) return "";
        Object val = rows.get(0).get("access_token");
        return val != null ? val.toString() : "";
    }

    /**
     * 왜: kollus_attach_channel.jsp에서 사이트별 기본 채널키를 결정합니다.
     */
    public String getDefaultChannelKey(long siteId) {
        String sql = "SELECT kollus_channel FROM TB_SITE WHERE id = :siteId";
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("siteId", siteId);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        if (rows.isEmpty()) return "";
        Object val = rows.get(0).get("kollus_channel");
        return val != null && !val.toString().isEmpty() ? val.toString() : "u8p6y0itgnuaemiy";
    }

    // ==================== 레슨 upsert ====================

    /**
     * 왜: media_content_key로 기존 레슨을 찾습니다.
     */
    public Optional<Map<String, Object>> findLessonByMediaKey(String mediaContentKey, long siteId) {
        String sql = """
                SELECT id, lesson_nm, total_time, content_width, content_height
                FROM LM_LESSON
                WHERE media_content_key = :mediaContentKey AND site_id = :siteId AND status != -1
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("mediaContentKey", mediaContentKey)
                .addValue("siteId", siteId);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void updateLesson(int lessonId, String title, int totalTime, int contentWidth, int contentHeight) {
        String sql = """
                UPDATE LM_LESSON
                SET lesson_nm = :title, total_time = :totalTime,
                    content_width = :contentWidth, content_height = :contentHeight
                WHERE id = :lessonId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lessonId", lessonId)
                .addValue("title", title)
                .addValue("totalTime", totalTime)
                .addValue("contentWidth", contentWidth)
                .addValue("contentHeight", contentHeight);
        jdbc.update(sql, params);
    }

    public long insertLesson(long siteId, String mediaContentKey, String title,
                              int totalTime, int contentWidth, int contentHeight) {
        // 왜: lesson_type = '03' (Kollus 영상)
        String sql = """
                INSERT INTO LM_LESSON (site_id, lesson_nm, lesson_type, media_content_key,
                    total_time, content_width, content_height, status, reg_date)
                VALUES (:siteId, :title, '03', :mediaContentKey,
                    :totalTime, :contentWidth, :contentHeight, 1, NOW())
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", siteId)
                .addValue("title", title)
                .addValue("mediaContentKey", mediaContentKey)
                .addValue("totalTime", totalTime)
                .addValue("contentWidth", contentWidth)
                .addValue("contentHeight", contentHeight);
        jdbc.update(sql, params, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : 0;
    }

    // ==================== 찜(wishlist) ====================

    public List<Map<String, Object>> listWishlist(long userId, long siteId, int offset, int limit) {
        String sql = """
                SELECT w.id, w.media_content_key, w.title, w.thumbnail_url, w.reg_date
                FROM LM_KOLLUS_WISHLIST w
                WHERE w.user_id = :userId AND w.site_id = :siteId AND w.status != -1
                ORDER BY w.reg_date DESC
                LIMIT :limit OFFSET :offset
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("siteId", siteId)
                .addValue("offset", offset)
                .addValue("limit", limit);
        return jdbc.queryForList(sql, params);
    }

    public boolean isWishlisted(long userId, long siteId, String mediaContentKey) {
        String sql = """
                SELECT COUNT(*) FROM LM_KOLLUS_WISHLIST
                WHERE user_id = :userId AND site_id = :siteId AND media_content_key = :mediaContentKey AND status != -1
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("siteId", siteId)
                .addValue("mediaContentKey", mediaContentKey);
        Integer count = jdbc.queryForObject(sql, params, Integer.class);
        return count != null && count > 0;
    }

    public void addWishlist(long userId, long siteId, String mediaContentKey) {
        String sql = """
                INSERT INTO LM_KOLLUS_WISHLIST (user_id, site_id, media_content_key, status, reg_date)
                VALUES (:userId, :siteId, :mediaContentKey, 1, NOW())
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("siteId", siteId)
                .addValue("mediaContentKey", mediaContentKey);
        jdbc.update(sql, params);
    }

    public void removeWishlist(long userId, long siteId, String mediaContentKey) {
        String sql = """
                UPDATE LM_KOLLUS_WISHLIST SET status = -1
                WHERE user_id = :userId AND site_id = :siteId AND media_content_key = :mediaContentKey
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("siteId", siteId)
                .addValue("mediaContentKey", mediaContentKey);
        jdbc.update(sql, params);
    }

    /**
     * 왜: 레거시 찜 데이터를 새 테이블 구조로 마이그레이션합니다.
     *     TODO: 실제 마이그레이션 로직은 레거시 WishlistDao에 맞춰 구현 필요
     */
    public int migrateWishlist(long userId, long siteId) {
        // TODO: 마이그레이션 쿼리 구현
        return 0;
    }
}
