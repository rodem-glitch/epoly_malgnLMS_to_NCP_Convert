package kr.go.growailms.tutor.lesson;

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
 * 왜: external_link_lesson_upsert.jsp가 LessonDao로 하던 DB 접근을
 *     NamedParameterJdbcTemplate으로 대체합니다.
 *     테이블: LM_LESSON (lesson_type = '04' 외부링크)
 */
@Repository
public class TutorLessonJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(TutorLessonJdbcRepository.class);
    private final NamedParameterJdbcTemplate jdbc;

    public TutorLessonJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 외부링크 레슨 조회 ====================

    /**
     * 왜: 동일 URL의 외부링크 레슨이 이미 있는지 찾습니다.
     */
    public Optional<Map<String, Object>> findLessonByUrl(String url, long siteId) {
        String sql = """
                SELECT id, lesson_nm, total_time, lesson_link
                FROM LM_LESSON
                WHERE lesson_link = :url AND site_id = :siteId
                  AND lesson_type = '04' AND status != -1
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("url", url)
                .addValue("siteId", siteId);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    // ==================== 외부링크 레슨 등록 ====================

    /**
     * 왜: lesson_type = '04' (외부링크) 레슨을 새로 생성합니다.
     */
    public long insertExternalLesson(long siteId, String url, String title, int totalTime) {
        String sql = """
                INSERT INTO LM_LESSON (site_id, lesson_nm, lesson_type, lesson_link, total_time, status, reg_date)
                VALUES (:siteId, :title, '04', :url, :totalTime, 1, NOW())
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", siteId)
                .addValue("title", title)
                .addValue("url", url)
                .addValue("totalTime", totalTime);
        jdbc.update(sql, params, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : 0;
    }

    // ==================== 외부링크 레슨 수정 ====================

    /**
     * 왜: 기존 외부링크 레슨의 제목/시간을 업데이트합니다.
     */
    public void updateExternalLesson(int lessonId, String title, int totalTime) {
        String sql = """
                UPDATE LM_LESSON
                SET lesson_nm = :title, total_time = :totalTime
                WHERE id = :lessonId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lessonId", lessonId)
                .addValue("title", title)
                .addValue("totalTime", totalTime);
        jdbc.update(sql, params);
    }
}
