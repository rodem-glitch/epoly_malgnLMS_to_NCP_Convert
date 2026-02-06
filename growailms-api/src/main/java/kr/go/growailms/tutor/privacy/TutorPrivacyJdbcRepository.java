package kr.go.growailms.tutor.privacy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 왜: privacy_log.jsp가 InfoLogDao로 하던 DB 접근을
 *     NamedParameterJdbcTemplate으로 대체합니다.
 *     테이블: TB_INFO_LOG, LM_COURSE_TUTOR
 */
@Repository
public class TutorPrivacyJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(TutorPrivacyJdbcRepository.class);
    private final NamedParameterJdbcTemplate jdbc;

    public TutorPrivacyJdbcRepository(NamedParameterJdbcTemplate jdbc) {
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

    // ==================== privacy_log.jsp ====================

    /**
     * 왜: 개인정보 접근 로그를 기록합니다.
     *     log_type: V(조회), E(엑셀다운로드)
     */
    public long insertPrivacyLog(long siteId, long userId, String logType, String purpose,
                                  String pageNm, int courseId, String userIds, int userCnt) {
        String sql = """
                INSERT INTO TB_INFO_LOG (site_id, user_id, log_type, purpose, page_nm,
                    course_id, user_ids, user_cnt, reg_date)
                VALUES (:siteId, :userId, :logType, :purpose, :pageNm,
                    :courseId, :userIds, :userCnt, NOW())
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", siteId)
                .addValue("userId", userId)
                .addValue("logType", logType)
                .addValue("purpose", purpose)
                .addValue("pageNm", pageNm)
                .addValue("courseId", courseId)
                .addValue("userIds", userIds)
                .addValue("userCnt", userCnt);
        jdbc.update(sql, params, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : 0;
    }
}
