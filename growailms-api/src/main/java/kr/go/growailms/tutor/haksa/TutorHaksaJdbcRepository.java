package kr.go.growailms.tutor.haksa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * 왜: haksa_*.jsp가 사용하던 PolyCourseSettingDao, PolyCourseDao, PolyStudentDao 등의
 *     DB 접근을 NamedParameterJdbcTemplate으로 대체합니다.
 *     학사 시스템은 5-part 복합키(course_code, open_year, open_term, bunban_code, group_code)를 사용합니다.
 */
@Repository
public class TutorHaksaJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(TutorHaksaJdbcRepository.class);
    private final NamedParameterJdbcTemplate jdbc;

    public TutorHaksaJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 복합키 파라미터 생성 공통 ====================

    private MapSqlParameterSource compositeKeyParams(String courseCode, String openYear,
                                                      String openTerm, String bunbanCode,
                                                      String groupCode, long siteId) {
        return new MapSqlParameterSource()
                .addValue("courseCode", courseCode)
                .addValue("openYear", openYear)
                .addValue("openTerm", openTerm)
                .addValue("bunbanCode", bunbanCode)
                .addValue("groupCode", groupCode)
                .addValue("siteId", siteId);
    }

    // ==================== LM_POLY_COURSE_SETTING (JSON 설정 저장) ====================

    /**
     * 왜: 학사 과목의 커리큘럼/평가/시험 설정은 JSON으로 LM_POLY_COURSE_SETTING에 저장됩니다.
     */
    public Optional<Map<String, Object>> getCourseSetting(String courseCode, String openYear,
                                                           String openTerm, String bunbanCode,
                                                           String groupCode, long siteId) {
        String sql = """
                SELECT * FROM LM_POLY_COURSE_SETTING
                WHERE course_code = :courseCode AND open_year = :openYear
                  AND open_term = :openTerm AND bunban_code = :bunbanCode
                  AND group_code = :groupCode AND site_id = :siteId
                """;
        return jdbc.queryForList(sql, compositeKeyParams(courseCode, openYear, openTerm, bunbanCode, groupCode, siteId))
                .stream().findFirst();
    }

    public int upsertCourseSetting(String courseCode, String openYear, String openTerm,
                                    String bunbanCode, String groupCode, long siteId,
                                    String column, String value) {
        // 왜: 복합PK로 upsert합니다. 없으면 INSERT, 있으면 UPDATE.
        Optional<Map<String, Object>> existing = getCourseSetting(courseCode, openYear, openTerm,
                bunbanCode, groupCode, siteId);

        MapSqlParameterSource params = compositeKeyParams(courseCode, openYear, openTerm,
                bunbanCode, groupCode, siteId)
                .addValue("val", value);

        if (existing.isPresent()) {
            String sql = "UPDATE LM_POLY_COURSE_SETTING SET " + column + " = :val, mod_date = NOW() "
                    + "WHERE course_code = :courseCode AND open_year = :openYear "
                    + "AND open_term = :openTerm AND bunban_code = :bunbanCode "
                    + "AND group_code = :groupCode AND site_id = :siteId";
            return jdbc.update(sql, params);
        } else {
            String sql = "INSERT INTO LM_POLY_COURSE_SETTING "
                    + "(course_code, open_year, open_term, bunban_code, group_code, site_id, "
                    + column + ", reg_date) "
                    + "VALUES (:courseCode, :openYear, :openTerm, :bunbanCode, :groupCode, :siteId, "
                    + ":val, NOW())";
            return jdbc.update(sql, params);
        }
    }

    // ==================== LM_POLY_COURSE (학사 과목 미러) ====================

    public Optional<Map<String, Object>> findPolyCourse(String courseCode, String openYear,
                                                         String openTerm, String bunbanCode,
                                                         String groupCode, long siteId) {
        String sql = """
                SELECT * FROM LM_POLY_COURSE
                WHERE course_code = :courseCode AND open_year = :openYear
                  AND open_term = :openTerm AND bunban_code = :bunbanCode
                  AND group_code = :groupCode AND site_id = :siteId
                """;
        return jdbc.queryForList(sql, compositeKeyParams(courseCode, openYear, openTerm, bunbanCode, groupCode, siteId))
                .stream().findFirst();
    }

    // ==================== LM_POLY_STUDENT (학사 학생 미러) ====================

    public List<Map<String, Object>> listPolyStudents(String courseCode, String openYear,
                                                       String openTerm, String bunbanCode,
                                                       String groupCode, long siteId, String keyword) {
        StringBuilder sql = new StringBuilder("""
                SELECT ps.*, pm.user_nm, pm.login_id, pm.email
                FROM LM_POLY_STUDENT ps
                LEFT JOIN LM_POLY_MEMBER pm ON pm.member_key = ps.member_key AND pm.site_id = :siteId
                WHERE ps.course_code = :courseCode AND ps.open_year = :openYear
                  AND ps.open_term = :openTerm AND ps.bunban_code = :bunbanCode
                  AND ps.group_code = :groupCode AND ps.site_id = :siteId
                """);

        MapSqlParameterSource params = compositeKeyParams(courseCode, openYear, openTerm, bunbanCode, groupCode, siteId);

        if (!keyword.isEmpty()) {
            sql.append(" AND (pm.user_nm LIKE :keyword OR pm.login_id LIKE :keyword OR ps.member_key LIKE :keyword) ");
            params.addValue("keyword", "%" + keyword + "%");
        }
        sql.append(" ORDER BY ps.member_key ASC ");

        return jdbc.queryForList(sql.toString(), params);
    }

    // ==================== LM_POLY_COURSE_GRADE (학사 성적) ====================

    public List<Map<String, Object>> listGrades(String courseCode, String openYear,
                                                 String openTerm, String bunbanCode,
                                                 String groupCode, long siteId) {
        String sql = """
                SELECT * FROM LM_POLY_COURSE_GRADE
                WHERE course_code = :courseCode AND open_year = :openYear
                  AND open_term = :openTerm AND bunban_code = :bunbanCode
                  AND group_code = :groupCode AND site_id = :siteId
                ORDER BY member_key ASC
                """;
        return jdbc.queryForList(sql, compositeKeyParams(courseCode, openYear, openTerm, bunbanCode, groupCode, siteId));
    }

    public void deleteGrades(String courseCode, String openYear, String openTerm,
                              String bunbanCode, String groupCode, long siteId) {
        String sql = """
                DELETE FROM LM_POLY_COURSE_GRADE
                WHERE course_code = :courseCode AND open_year = :openYear
                  AND open_term = :openTerm AND bunban_code = :bunbanCode
                  AND group_code = :groupCode AND site_id = :siteId
                """;
        jdbc.update(sql, compositeKeyParams(courseCode, openYear, openTerm, bunbanCode, groupCode, siteId));
    }

    public void insertGrade(String courseCode, String openYear, String openTerm,
                             String bunbanCode, String groupCode, long siteId,
                             String memberKey, String grade) {
        String sql = """
                INSERT INTO LM_POLY_COURSE_GRADE
                (course_code, open_year, open_term, bunban_code, group_code, site_id,
                 member_key, grade, reg_date)
                VALUES (:courseCode, :openYear, :openTerm, :bunbanCode, :groupCode, :siteId,
                        :memberKey, :grade, NOW())
                """;
        MapSqlParameterSource params = compositeKeyParams(courseCode, openYear, openTerm, bunbanCode, groupCode, siteId)
                .addValue("memberKey", memberKey)
                .addValue("grade", grade);
        jdbc.update(sql, params);
    }

    // ==================== haksa_resolve: LMS 과목 매핑 ====================

    /**
     * 왜: haksa_resolve.jsp의 핵심 로직. 학사 과목에 대응하는 LMS 과목(LM_COURSE)을 찾거나 생성합니다.
     */
    public Optional<Map<String, Object>> findMappedLmsCourse(String courseCode, String openYear,
                                                               String openTerm, long siteId) {
        String sql = """
                SELECT c.* FROM LM_COURSE c
                WHERE c.course_cd = :courseCode AND c.year = :openYear
                  AND c.site_id = :siteId AND c.status != -1
                ORDER BY c.id DESC LIMIT 1
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("courseCode", courseCode)
                .addValue("openYear", openYear)
                .addValue("siteId", siteId))
                .stream().findFirst();
    }

    public long createLmsCourseFromHaksa(String courseCode, String courseName, String openYear,
                                          long siteId) {
        String sql = """
                INSERT INTO LM_COURSE (site_id, course_cd, course_nm, year, step,
                    course_type, onoff_type, display_yn, status, reg_date)
                VALUES (:siteId, :courseCode, :courseName, :openYear, 1,
                    'R', 'O', 'Y', 1, NOW())
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("siteId", siteId)
                .addValue("courseCode", courseCode)
                .addValue("courseName", courseName)
                .addValue("openYear", openYear), keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    // ==================== 동기화 로그 ====================

    public Optional<Map<String, Object>> getLastSyncLog(String tableName, long siteId) {
        String sql = """
                SELECT sync_date FROM LM_SYNC_LOG
                WHERE table_name = :tableName AND site_id = :siteId
                ORDER BY sync_date DESC LIMIT 1
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("tableName", tableName).addValue("siteId", siteId))
                .stream().findFirst();
    }

    // ==================== member_key 매핑 ====================

    public Optional<String> findMemberKeyByLoginId(String loginId, long siteId) {
        String sql = """
                SELECT member_key FROM LM_POLY_MEMBER_KEY
                WHERE (alias_key = :loginId OR member_key = :loginId)
                  AND site_id = :siteId
                LIMIT 1
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("loginId", loginId).addValue("siteId", siteId))
                .stream().findFirst()
                .map(row -> (String) row.get("member_key"));
    }

    public Optional<String> getLoginId(long userId, long siteId) {
        String sql = "SELECT login_id FROM TB_USER WHERE id = :userId AND site_id = :siteId";
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("userId", userId).addValue("siteId", siteId))
                .stream().findFirst()
                .map(row -> (String) row.get("login_id"));
    }
}
