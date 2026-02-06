package kr.go.growailms.tutor.program;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 왜: 레거시 DAO(SubjectDao, SubjectPlanDao)가 하던 DB 접근을
 *     Spring Boot의 NamedParameterJdbcTemplate으로 대체합니다.
 *     테이블: LM_SUBJECT, LM_SUBJECT_PLAN, LM_COURSE
 */
@Repository
public class TutorProgramJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(TutorProgramJdbcRepository.class);
    private final NamedParameterJdbcTemplate jdbc;

    public TutorProgramJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 권한 확인 ====================

    /**
     * 왜: 교수자가 이 프로그램에 속한 과목의 주담당인지 확인합니다.
     *     프로그램(LM_SUBJECT)에 속한 과목(LM_COURSE) 중 하나라도 주담당이면 접근 가능합니다.
     */
    public boolean isProgramTutor(long userId, int programId, long siteId) {
        String sql = """
                SELECT COUNT(*) FROM LM_COURSE_TUTOR ct
                INNER JOIN LM_COURSE c ON c.id = ct.course_id AND c.site_id = :siteId AND c.status != -1
                WHERE c.subject_id = :programId
                  AND ct.user_id = :userId
                  AND ct.type = 'major'
                  AND ct.site_id = :siteId
                  AND ct.status != -1
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("programId", programId)
                .addValue("userId", userId)
                .addValue("siteId", siteId);
        Integer count = jdbc.queryForObject(sql, params, Integer.class);
        return count != null && count > 0;
    }

    // ==================== program_list.jsp ====================

    public List<Map<String, Object>> listPrograms(long userId, long siteId, boolean isAdmin,
                                                   String keyword, String year) {
        StringBuilder sql = new StringBuilder("""
                SELECT s.id, s.course_nm, s.year, s.start_date, s.end_date,
                       s.description, s.category_id, s.status, s.reg_date,
                       (SELECT COUNT(*) FROM LM_COURSE c
                        WHERE c.subject_id = s.id AND c.site_id = :siteId AND c.status != -1) AS course_cnt
                FROM LM_SUBJECT s
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", siteId);

        // 왜: 교수자는 본인이 주담당인 과목이 속한 프로그램만 봅니다.
        if (!isAdmin) {
            sql.append("""
                    INNER JOIN LM_COURSE c2 ON c2.subject_id = s.id AND c2.site_id = :siteId AND c2.status != -1
                    INNER JOIN LM_COURSE_TUTOR ct ON ct.course_id = c2.id
                        AND ct.user_id = :userId AND ct.type = 'major' AND ct.site_id = :siteId AND ct.status != -1
                    """);
            params.addValue("userId", userId);
        }

        sql.append(" WHERE s.site_id = :siteId AND s.status != -1 ");

        if (!year.isEmpty()) {
            sql.append(" AND s.year = :year ");
            params.addValue("year", year);
        }
        if (!keyword.isEmpty()) {
            sql.append(" AND s.course_nm LIKE :keyword ");
            params.addValue("keyword", "%" + keyword + "%");
        }

        // 왜: 교수자 JOIN 사용 시 동일 프로그램이 중복될 수 있으므로 GROUP BY를 적용합니다.
        if (!isAdmin) {
            sql.append(" GROUP BY s.id, s.course_nm, s.year, s.start_date, s.end_date, ")
               .append("s.description, s.category_id, s.status, s.reg_date ");
        }

        sql.append(" ORDER BY s.id DESC ");

        return jdbc.queryForList(sql.toString(), params);
    }

    // ==================== program_view.jsp ====================

    public Optional<Map<String, Object>> getProgram(int programId, long siteId) {
        String sql = """
                SELECT s.*,
                       (SELECT COUNT(*) FROM LM_COURSE c
                        WHERE c.subject_id = s.id AND c.site_id = :siteId AND c.status != -1) AS course_cnt
                FROM LM_SUBJECT s
                WHERE s.id = :programId AND s.site_id = :siteId AND s.status != -1
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("programId", programId)
                .addValue("siteId", siteId)).stream().findFirst();
    }

    public List<Map<String, Object>> listSubjectPlans(int programId, long siteId) {
        String sql = """
                SELECT id, subject_id, plan_nm, plan_type, plan_date, description, sort, status
                FROM LM_SUBJECT_PLAN
                WHERE subject_id = :programId AND site_id = :siteId AND status != -1
                ORDER BY sort ASC, id ASC
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("programId", programId)
                .addValue("siteId", siteId));
    }

    // ==================== program_insert.jsp ====================

    public long insertProgram(long siteId, String courseNm, String year, String startDate,
                              String endDate, String description, int categoryId) {
        String sql = """
                INSERT INTO LM_SUBJECT (site_id, course_nm, year, start_date, end_date,
                    description, category_id, status, reg_date)
                VALUES (:siteId, :courseNm, :year, :startDate, :endDate,
                    :description, :categoryId, 1, NOW())
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("siteId", siteId)
                .addValue("courseNm", courseNm)
                .addValue("year", year)
                .addValue("startDate", startDate)
                .addValue("endDate", endDate)
                .addValue("description", description)
                .addValue("categoryId", categoryId), keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    // ==================== program_modify.jsp ====================

    public int updateProgram(int programId, long siteId, String courseNm, String year,
                             String startDate, String endDate, String description, int categoryId) {
        String sql = """
                UPDATE LM_SUBJECT SET
                    course_nm = :courseNm, year = :year, start_date = :startDate,
                    end_date = :endDate, description = :description,
                    category_id = :categoryId, mod_date = NOW()
                WHERE id = :programId AND site_id = :siteId AND status != -1
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("programId", programId)
                .addValue("siteId", siteId)
                .addValue("courseNm", courseNm)
                .addValue("year", year)
                .addValue("startDate", startDate)
                .addValue("endDate", endDate)
                .addValue("description", description)
                .addValue("categoryId", categoryId));
    }

    // ==================== program_delete.jsp ====================

    public int countActiveCourses(int programId, long siteId) {
        String sql = """
                SELECT COUNT(*) FROM LM_COURSE
                WHERE subject_id = :programId AND site_id = :siteId AND status != -1
                """;
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("programId", programId)
                .addValue("siteId", siteId), Integer.class);
        return count != null ? count : 0;
    }

    public int softDeleteProgram(int programId, long siteId) {
        // 왜: 프로그램 삭제는 소프트 삭제(status=-1)입니다.
        String sql = """
                UPDATE LM_SUBJECT SET status = -1, mod_date = NOW()
                WHERE id = :programId AND site_id = :siteId AND status != -1
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("programId", programId)
                .addValue("siteId", siteId));
    }

    // ==================== program_course_list.jsp ====================

    public List<Map<String, Object>> listProgramCourses(int programId, long userId, long siteId,
                                                         boolean isAdmin, String year) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.course_nm, c.year, c.step, c.course_type, c.onoff_type,
                       c.study_sdate, c.study_edate, c.status,
                       (SELECT COUNT(*) FROM LM_COURSE_USER cu
                        WHERE cu.course_id = c.id AND cu.site_id = :siteId AND cu.status != -1) AS student_cnt
                FROM LM_COURSE c
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", siteId)
                .addValue("programId", programId);

        // 왜: 교수자는 본인 과목만 봅니다.
        if (!isAdmin) {
            sql.append("""
                    INNER JOIN LM_COURSE_TUTOR ct ON ct.course_id = c.id
                        AND ct.user_id = :userId AND ct.type = 'major' AND ct.site_id = :siteId AND ct.status != -1
                    """);
            params.addValue("userId", userId);
        }

        sql.append(" WHERE c.subject_id = :programId AND c.site_id = :siteId AND c.status != -1 ");

        if (!year.isEmpty()) {
            sql.append(" AND c.year = :year ");
            params.addValue("year", year);
        }

        sql.append(" ORDER BY c.year DESC, c.step ASC ");

        return jdbc.queryForList(sql.toString(), params);
    }
}
