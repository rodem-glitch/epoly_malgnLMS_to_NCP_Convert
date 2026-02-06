package kr.go.growailms.tutor.course;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * 왜: 레거시 DAO(CourseDao, CourseTutorDao 등)가 하던 DB 접근을
 *     Spring Boot의 NamedParameterJdbcTemplate으로 대체합니다.
 *     테이블/컬럼명은 레거시와 동일하게 유지하여 데이터 호환성을 보장합니다.
 */
@Repository
public class TutorCourseJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(TutorCourseJdbcRepository.class);
    private final NamedParameterJdbcTemplate jdbc;

    public TutorCourseJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 권한 확인 ====================

    /**
     * 왜: init.jsp에서 CourseTutorDao로 하던 "이 교수자가 이 과목의 주담당인지" 확인을
     *     한 번의 쿼리로 수행합니다.
     */
    public boolean isMajorTutor(long userId, int courseId, long siteId) {
        String sql = """
                SELECT COUNT(*) FROM LM_COURSE_TUTOR
                WHERE course_id = :courseId
                  AND user_id = :userId
                  AND type = 'major'
                  AND site_id = :siteId
                  AND status != -1
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("userId", userId)
                .addValue("siteId", siteId);

        Integer count = jdbc.queryForObject(sql, params, Integer.class);
        return count != null && count > 0;
    }

    // ==================== course_list.jsp ====================

    /**
     * 왜: course_list.jsp의 메인 쿼리를 재현합니다.
     *     교수자는 본인 과목만, 관리자는 전체를 조회합니다.
     */
    public List<Map<String, Object>> listCourses(long userId, long siteId, boolean isAdmin,
                                                  String keyword, String year, int tutorId) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.course_cd, c.course_nm, c.year, c.step,
                       c.course_type, c.onoff_type, c.study_sdate, c.study_edate,
                       c.request_sdate, c.request_edate, c.status,
                       c.subject_id AS program_id,
                       s.course_nm AS program_nm,
                       s.start_date AS program_start_date,
                       s.end_date AS program_end_date,
                       (SELECT COUNT(*) FROM LM_COURSE_USER cu
                        WHERE cu.site_id = :siteId AND cu.course_id = c.id AND cu.status != -1) AS student_cnt
                FROM LM_COURSE c
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", siteId);

        // 왜: 교수자는 본인 과목만 보도록 INNER JOIN으로 필터링합니다.
        if (!isAdmin) {
            sql.append(" INNER JOIN LM_COURSE_TUTOR ct ON ct.course_id = c.id")
               .append("   AND ct.user_id = :tutorUserId AND ct.type = 'major' AND ct.site_id = :siteId ");
            params.addValue("tutorUserId", userId);
        } else if (tutorId > 0) {
            sql.append(" INNER JOIN LM_COURSE_TUTOR ct ON ct.course_id = c.id")
               .append("   AND ct.user_id = :tutorUserId AND ct.type = 'major' AND ct.site_id = :siteId ");
            params.addValue("tutorUserId", tutorId);
        }

        sql.append(" LEFT JOIN LM_SUBJECT s ON s.id = c.subject_id AND s.site_id = :siteId AND s.status != -1 ");
        sql.append(" WHERE c.site_id = :siteId AND c.status != -1 AND c.onoff_type != 'P' ");

        if (!year.isEmpty()) {
            sql.append(" AND c.year = :year ");
            params.addValue("year", year);
        }
        if (!keyword.isEmpty()) {
            sql.append(" AND (c.course_nm LIKE :keyword OR s.course_nm LIKE :keyword OR CAST(c.id AS CHAR) LIKE :keyword) ");
            params.addValue("keyword", "%" + keyword + "%");
        }

        sql.append(" ORDER BY c.id DESC ");

        return jdbc.queryForList(sql.toString(), params);
    }

    // ==================== course_view.jsp / course_info_get.jsp ====================

    public Optional<Map<String, Object>> getCourseDetail(int courseId, long siteId) {
        String sql = """
                SELECT c.*, s.course_nm AS program_nm,
                       s.start_date AS program_start_date, s.end_date AS program_end_date,
                       (SELECT COUNT(*) FROM LM_COURSE_USER cu
                        WHERE cu.site_id = :siteId AND cu.course_id = c.id AND cu.status != -1) AS student_cnt
                FROM LM_COURSE c
                LEFT JOIN LM_SUBJECT s ON s.id = c.subject_id AND s.site_id = :siteId AND s.status != -1
                WHERE c.id = :courseId AND c.site_id = :siteId AND c.status != -1
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("siteId", siteId);

        return jdbc.queryForList(sql, params).stream().findFirst();
    }

    // ==================== course_info_update.jsp ====================

    public int updateCourseInfo(int courseId, long siteId, String content1, String content2) {
        String sql = """
                UPDATE LM_COURSE SET content1 = :content1, content2 = :content2
                WHERE id = :courseId AND site_id = :siteId AND status != -1
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("siteId", siteId)
                .addValue("content1", content1)
                .addValue("content2", content2);
        return jdbc.update(sql, params);
    }

    // ==================== course_insert.jsp ====================

    /**
     * 왜: course_insert.jsp의 step 자동계산(같은 program_id+year의 기존 과목 수 + 1)을 재현합니다.
     */
    public int calcNextStep(int programId, String year, long siteId) {
        String sql = """
                SELECT COUNT(*) FROM LM_COURSE
                WHERE subject_id = :programId AND year = :year AND site_id = :siteId AND status != -1
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("programId", programId)
                .addValue("year", year)
                .addValue("siteId", siteId);
        Integer count = jdbc.queryForObject(sql, params, Integer.class);
        return (count != null ? count : 0) + 1;
    }

    public long insertCourse(Map<String, Object> courseData) {
        String sql = """
                INSERT INTO LM_COURSE (site_id, subject_id, course_nm, year, step,
                    study_sdate, study_edate, category_id, course_type, onoff_type,
                    content1, content2, course_file, display_yn, status, reg_date)
                VALUES (:site_id, :subject_id, :course_nm, :year, :step,
                    :study_sdate, :study_edate, :category_id, 'R', 'O',
                    :content1, :content2, :course_file, 'Y', 1, NOW())
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, new MapSqlParameterSource(courseData), keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public void insertCourseTutor(long courseId, long userId, long siteId) {
        String sql = """
                INSERT INTO LM_COURSE_TUTOR (course_id, user_id, type, site_id, status, reg_date)
                VALUES (:courseId, :userId, 'major', :siteId, 1, NOW())
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("userId", userId)
                .addValue("siteId", siteId));
    }

    // ==================== course_categories.jsp ====================

    public List<Map<String, Object>> listCategories(long siteId) {
        String sql = """
                SELECT id, category_nm, parent_id, depth, sort
                FROM LM_CATEGORY
                WHERE site_id = :siteId AND module = 'course' AND status != -1
                ORDER BY sort ASC, id ASC
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource("siteId", siteId));
    }

    // ==================== course_years.jsp ====================

    public List<Map<String, Object>> listYears(long userId, long siteId, boolean isAdmin, int tutorId) {
        StringBuilder sql = new StringBuilder("SELECT DISTINCT c.year FROM LM_COURSE c ");

        MapSqlParameterSource params = new MapSqlParameterSource("siteId", siteId);

        if (!isAdmin) {
            sql.append(" INNER JOIN LM_COURSE_TUTOR ct ON ct.course_id = c.id")
               .append("   AND ct.user_id = :userId AND ct.type = 'major' AND ct.site_id = :siteId ");
            params.addValue("userId", userId);
        } else if (tutorId > 0) {
            sql.append(" INNER JOIN LM_COURSE_TUTOR ct ON ct.course_id = c.id")
               .append("   AND ct.user_id = :tutorId AND ct.type = 'major' AND ct.site_id = :siteId ");
            params.addValue("tutorId", tutorId);
        }

        sql.append(" WHERE c.site_id = :siteId AND c.status != -1 ORDER BY c.year DESC ");

        return jdbc.queryForList(sql.toString(), params);
    }

    // ==================== course_students_list.jsp ====================

    public List<Map<String, Object>> listStudents(int courseId, long siteId, String keyword) {
        StringBuilder sql = new StringBuilder("""
                SELECT cu.id AS course_user_id, cu.user_id, cu.course_id,
                       cu.progress_ratio, cu.total_score, cu.complete_yn, cu.complete_status,
                       cu.complete_no, cu.start_date, cu.end_date, cu.status,
                       u.login_id, u.user_nm, u.email
                FROM LM_COURSE_USER cu
                INNER JOIN TB_USER u ON u.id = cu.user_id AND u.site_id = :siteId
                WHERE cu.course_id = :courseId AND cu.site_id = :siteId
                  AND cu.status NOT IN (-1, -4)
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("siteId", siteId);

        if (!keyword.isEmpty()) {
            sql.append(" AND (u.user_nm LIKE :keyword OR u.login_id LIKE :keyword OR u.email LIKE :keyword) ");
            params.addValue("keyword", "%" + keyword + "%");
        }
        sql.append(" ORDER BY u.login_id ASC ");

        return jdbc.queryForList(sql.toString(), params);
    }

    // ==================== course_students_add.jsp ====================

    public Optional<Map<String, Object>> findUserByIdOrLoginId(String token, long siteId) {
        try {
            long numericId = Long.parseLong(token.trim());
            String sql = "SELECT id, login_id FROM TB_USER WHERE id = :id AND site_id = :siteId AND status = 1";
            return jdbc.queryForList(sql, new MapSqlParameterSource("id", numericId).addValue("siteId", siteId))
                    .stream().findFirst();
        } catch (NumberFormatException e) {
            String sql = "SELECT id, login_id FROM TB_USER WHERE login_id = :loginId AND site_id = :siteId AND status = 1";
            return jdbc.queryForList(sql, new MapSqlParameterSource("loginId", token.trim()).addValue("siteId", siteId))
                    .stream().findFirst();
        }
    }

    public boolean isAlreadyEnrolled(int courseId, long userId, long siteId) {
        String sql = """
                SELECT COUNT(*) FROM LM_COURSE_USER
                WHERE course_id = :courseId AND user_id = :userId AND site_id = :siteId AND status != -1
                """;
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId).addValue("userId", userId).addValue("siteId", siteId), Integer.class);
        return count != null && count > 0;
    }

    public void insertCourseUser(int courseId, long userId, long siteId) {
        String sql = """
                INSERT INTO LM_COURSE_USER (course_id, user_id, site_id, status, progress_ratio,
                    total_score, complete_yn, reg_date)
                VALUES (:courseId, :userId, :siteId, 1, 0, 0, 'N', NOW())
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId).addValue("userId", userId).addValue("siteId", siteId));
    }

    // ==================== course_students_remove.jsp ====================

    public int softRemoveStudent(int courseId, int userId, long siteId) {
        // 왜: 수강 취소는 하드 삭제가 아닌 status=-4로 소프트 삭제합니다.
        String sql = """
                UPDATE LM_COURSE_USER SET status = -4, mod_date = NOW()
                WHERE course_id = :courseId AND user_id = :userId AND site_id = :siteId
                  AND status NOT IN (-1, -4)
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId).addValue("userId", userId).addValue("siteId", siteId));
    }

    // ==================== course_set_program.jsp ====================

    public int updateCourseProgram(int courseId, int programId, long siteId) {
        String sql = """
                UPDATE LM_COURSE SET subject_id = :programId
                WHERE id = :courseId AND site_id = :siteId AND status != -1
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId).addValue("programId", programId).addValue("siteId", siteId));
    }

    public boolean programExists(int programId, long siteId) {
        String sql = "SELECT COUNT(*) FROM LM_SUBJECT WHERE id = :id AND site_id = :siteId AND status != -1";
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource("id", programId).addValue("siteId", siteId), Integer.class);
        return count != null && count > 0;
    }

    // ==================== course_evaluation ====================

    public Optional<Map<String, Object>> getEvaluation(int courseId, long siteId) {
        String sql = """
                SELECT id, assign_progress, assign_exam, assign_homework, assign_forum, assign_etc,
                       limit_progress, limit_total_score, assign_survey_yn, push_survey_yn, pass_yn
                FROM LM_COURSE
                WHERE id = :courseId AND site_id = :siteId AND status != -1
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource("courseId", courseId).addValue("siteId", siteId))
                .stream().findFirst();
    }

    public int updateEvaluation(int courseId, long siteId, Map<String, String> params) {
        String sql = """
                UPDATE LM_COURSE SET
                    assign_progress = :assign_progress, assign_exam = :assign_exam,
                    assign_homework = :assign_homework, assign_forum = :assign_forum,
                    assign_etc = :assign_etc, limit_progress = :limit_progress,
                    limit_total_score = :limit_total_score,
                    assign_survey_yn = :assign_survey_yn, push_survey_yn = :push_survey_yn,
                    pass_yn = :pass_yn
                WHERE id = :courseId AND site_id = :siteId AND status != -1
                """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("siteId", siteId)
                .addValue("assign_progress", intParam(params, "assign_progress", 100))
                .addValue("assign_exam", intParam(params, "assign_exam", 0))
                .addValue("assign_homework", intParam(params, "assign_homework", 0))
                .addValue("assign_forum", intParam(params, "assign_forum", 0))
                .addValue("assign_etc", intParam(params, "assign_etc", 0))
                .addValue("limit_progress", intParam(params, "limit_progress", 60))
                .addValue("limit_total_score", intParam(params, "limit_total_score", 60))
                .addValue("assign_survey_yn", params.getOrDefault("assign_survey_yn", "N"))
                .addValue("push_survey_yn", params.getOrDefault("push_survey_yn", "N"))
                .addValue("pass_yn", params.getOrDefault("pass_yn", "N"));
        return jdbc.update(sql, p);
    }

    // ==================== course_certificate_update.jsp ====================

    public int updateCertificate(int courseId, long siteId, Map<String, String> params) {
        String sql = """
                UPDATE LM_COURSE SET
                    cert_complete_yn = :cert_complete_yn,
                    cert_template_id = :cert_template_id,
                    pass_cert_template_id = :pass_cert_template_id,
                    complete_no_yn = :complete_no_yn
                WHERE id = :courseId AND site_id = :siteId AND status != -1
                """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("siteId", siteId)
                .addValue("cert_complete_yn", params.getOrDefault("cert_complete_yn", "N"))
                .addValue("cert_template_id", intParam(params, "cert_template_id", 0))
                .addValue("pass_cert_template_id", intParam(params, "pass_cert_template_id", 0))
                .addValue("complete_no_yn", params.getOrDefault("complete_no_yn", "N"));
        return jdbc.update(sql, p);
    }

    // ==================== course_copy.jsp ====================

    public long copyCourse(int sourceCourseId, String courseNm, int tutorId, long siteId) {
        // 왜: 원본 과목의 모든 필드를 복사하되, 이름/교수자/상태만 새로 설정합니다.
        String sql = """
                INSERT INTO LM_COURSE (site_id, subject_id, course_nm, year, step, course_cd,
                    course_type, onoff_type, study_sdate, study_edate, request_sdate, request_edate,
                    content1, content2, display_yn, sale_yn, close_yn, status, reg_date,
                    assign_progress, assign_exam, assign_homework, assign_forum, assign_etc,
                    limit_progress, limit_total_score, category_id)
                SELECT site_id, subject_id, :courseNm, year,
                    (SELECT COUNT(*) + 1 FROM LM_COURSE c2 WHERE c2.subject_id = c1.subject_id AND c2.year = c1.year AND c2.site_id = :siteId AND c2.status != -1),
                    course_cd, course_type, onoff_type, study_sdate, study_edate, request_sdate, request_edate,
                    content1, content2, 'Y', 'N', 'N', 1, NOW(),
                    assign_progress, assign_exam, assign_homework, assign_forum, assign_etc,
                    limit_progress, limit_total_score, category_id
                FROM LM_COURSE c1
                WHERE id = :sourceId AND site_id = :siteId AND status != -1
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("courseNm", courseNm)
                .addValue("sourceId", sourceCourseId)
                .addValue("siteId", siteId), keyHolder);

        long newId = Objects.requireNonNull(keyHolder.getKey()).longValue();

        // 왜: 주담당 교수자를 새로 지정합니다.
        insertCourseTutor(newId, tutorId, siteId);

        // 왜: 커리큘럼(섹션 + 레슨)도 복사합니다.
        copySections(sourceCourseId, newId, siteId);
        copyLessons(sourceCourseId, newId, siteId);

        log.info("과목 복사 완료: sourceId={}, newId={}, tutorId={}", sourceCourseId, newId, tutorId);
        return newId;
    }

    private void copySections(int sourceId, long newId, long siteId) {
        String sql = """
                INSERT INTO LM_COURSE_SECTION (course_id, section_nm, sort, site_id, status, reg_date)
                SELECT :newId, section_nm, sort, site_id, status, NOW()
                FROM LM_COURSE_SECTION
                WHERE course_id = :sourceId AND site_id = :siteId AND status != -1
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("newId", newId).addValue("sourceId", sourceId).addValue("siteId", siteId));
    }

    private void copyLessons(int sourceId, long newId, long siteId) {
        String sql = """
                INSERT INTO LM_COURSE_LESSON (course_id, lesson_id, chapter, section_id, complete_time,
                    start_date, end_date, site_id, status, reg_date)
                SELECT :newId, lesson_id, chapter, section_id, complete_time,
                    start_date, end_date, site_id, status, NOW()
                FROM LM_COURSE_LESSON
                WHERE course_id = :sourceId AND site_id = :siteId AND status != -1
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("newId", newId).addValue("sourceId", sourceId).addValue("siteId", siteId));
    }

    // ==================== 유틸리티 ====================

    private int intParam(Map<String, String> params, String key, int defaultVal) {
        String v = params.get(key);
        if (v == null || v.isBlank()) return defaultVal;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
