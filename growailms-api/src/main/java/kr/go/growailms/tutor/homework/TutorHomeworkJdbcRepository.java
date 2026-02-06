package kr.go.growailms.tutor.homework;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * 왜: 레거시 DAO(HomeworkDao, HomeworkUserDao, HomeworkTaskDao, CourseModuleDao 등)가 하던 DB 접근을
 *     Spring Boot의 NamedParameterJdbcTemplate으로 대체합니다.
 *     테이블/컬럼명은 레거시와 동일하게 유지하여 데이터 호환성을 보장합니다.
 *
 * 주요 테이블: LM_HOMEWORK, LM_HOMEWORK_USER, LM_HOMEWORK_TASK,
 *             LM_COURSE_MODULE (module='homework'), LM_COURSE_USER, TB_USER
 */
@Repository
public class TutorHomeworkJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(TutorHomeworkJdbcRepository.class);
    private final NamedParameterJdbcTemplate jdbc;

    public TutorHomeworkJdbcRepository(NamedParameterJdbcTemplate jdbc) {
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

    // ==================== 존재 확인 ====================

    /**
     * 왜: course.find("id = " + courseId + " AND site_id = " + siteId + " AND status != -1") 재현
     */
    public boolean courseExists(int courseId, long siteId) {
        String sql = """
                SELECT COUNT(*) FROM LM_COURSE
                WHERE id = :courseId AND site_id = :siteId AND status != -1
                """;
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId).addValue("siteId", siteId), Integer.class);
        return count != null && count > 0;
    }

    /**
     * 왜: courseModule.find("course_id = X AND module = 'homework' AND module_id = Y AND status = 1") 재현
     */
    public boolean courseModuleExists(int courseId, int homeworkId) {
        String sql = """
                SELECT COUNT(*) FROM LM_COURSE_MODULE
                WHERE course_id = :courseId AND module = 'homework'
                  AND module_id = :homeworkId AND status = 1
                """;
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId).addValue("homeworkId", homeworkId), Integer.class);
        return count != null && count > 0;
    }

    /**
     * 왜: homework.find("id = " + homeworkId + " AND site_id = " + siteId + " AND status != -1") 재현
     */
    public boolean homeworkExists(int homeworkId, long siteId) {
        String sql = """
                SELECT COUNT(*) FROM LM_HOMEWORK
                WHERE id = :homeworkId AND site_id = :siteId AND status != -1
                """;
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("homeworkId", homeworkId).addValue("siteId", siteId), Integer.class);
        return count != null && count > 0;
    }

    /**
     * 왜: courseUser.find("id = X AND course_id = Y AND site_id = Z AND status IN (1,3)") 재현
     */
    public boolean courseUserExists(int courseUserId, int courseId, long siteId) {
        String sql = """
                SELECT COUNT(*) FROM LM_COURSE_USER
                WHERE id = :courseUserId AND course_id = :courseId
                  AND site_id = :siteId AND status IN (1, 3)
                """;
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("courseUserId", courseUserId).addValue("courseId", courseId)
                .addValue("siteId", siteId), Integer.class);
        return count != null && count > 0;
    }

    /**
     * 왜: homeworkTask.find("id = X AND course_id = Y AND status = 1") 재현
     */
    public boolean homeworkTaskExists(int taskId, int courseId) {
        String sql = """
                SELECT COUNT(*) FROM LM_HOMEWORK_TASK
                WHERE id = :taskId AND course_id = :courseId AND status = 1
                """;
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("taskId", taskId).addValue("courseId", courseId), Integer.class);
        return count != null && count > 0;
    }

    /**
     * 왜: 제출/채점 내역이 있는지 확인하여 삭제 가능 여부를 판단합니다.
     */
    public boolean hasHomeworkUserRecords(int homeworkId, int courseId) {
        String sql = """
                SELECT COUNT(*) FROM LM_HOMEWORK_USER
                WHERE homework_id = :homeworkId AND course_id = :courseId AND status = 1
                """;
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("homeworkId", homeworkId).addValue("courseId", courseId), Integer.class);
        return count != null && count > 0;
    }

    /**
     * 왜: 다른 과목에도 배치되어 있는지 확인합니다. 없으면 과제 자체를 소프트 삭제합니다.
     */
    public boolean isHomeworkUsedInOtherCourses(int homeworkId) {
        String sql = """
                SELECT COUNT(*) FROM LM_COURSE_MODULE
                WHERE module = 'homework' AND module_id = :homeworkId
                """;
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("homeworkId", homeworkId), Integer.class);
        return count != null && count > 0;
    }

    // ==================== homework_list.jsp ====================

    /**
     * 왜: homework_list.jsp의 메인 쿼리를 재현합니다.
     *     과목에 배치된 과제 목록 + 수강생 수 / 제출 수 / 채점완료 수를 함께 조회합니다.
     */
    public List<Map<String, Object>> listHomeworks(int courseId, long siteId) {
        String sql = """
                SELECT a.module_id AS homework_id, a.module_nm, a.apply_type,
                       a.start_date, a.end_date, a.chapter, a.assign_score,
                       h.homework_nm, h.onoff_type, h.content,
                       (SELECT COUNT(*) FROM LM_COURSE_USER cu
                        WHERE cu.site_id = :siteId AND cu.course_id = a.course_id
                          AND cu.status IN (1, 3)) AS total_cnt,
                       (SELECT COUNT(*) FROM LM_HOMEWORK_USER hu
                        INNER JOIN LM_COURSE_USER cu ON cu.id = hu.course_user_id AND cu.status IN (1, 3)
                        WHERE hu.homework_id = a.module_id AND hu.course_id = a.course_id
                          AND hu.status = 1 AND hu.submit_yn = 'Y') AS submitted_cnt,
                       (SELECT COUNT(*) FROM LM_HOMEWORK_USER hu
                        INNER JOIN LM_COURSE_USER cu ON cu.id = hu.course_user_id AND cu.status IN (1, 3)
                        WHERE hu.homework_id = a.module_id AND hu.course_id = a.course_id
                          AND hu.status = 1 AND hu.submit_yn = 'Y' AND hu.confirm_yn = 'Y') AS confirmed_cnt
                FROM LM_COURSE_MODULE a
                INNER JOIN LM_HOMEWORK h ON a.module_id = h.id AND h.site_id = :siteId AND h.status != -1
                WHERE a.course_id = :courseId AND a.module = 'homework' AND a.status = 1
                ORDER BY a.start_date ASC, a.end_date ASC, a.period ASC, a.module_id ASC
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId).addValue("siteId", siteId));
    }

    // ==================== homework_insert.jsp ====================

    /**
     * 왜: LM_HOMEWORK 신규 생성. homework.getSequence() + homework.insert() 재현.
     */
    public long insertHomework(long siteId, long managerId, String title, String content, String onoffType) {
        String sql = """
                INSERT INTO LM_HOMEWORK (site_id, onoff_type, category_id, homework_nm, content,
                    manager_id, reg_date, status)
                VALUES (:siteId, :onoffType, 0, :title, :content,
                    :managerId, NOW(), 1)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int rows = jdbc.update(sql, new MapSqlParameterSource()
                .addValue("siteId", siteId)
                .addValue("onoffType", onoffType)
                .addValue("title", title)
                .addValue("content", content)
                .addValue("managerId", managerId), keyHolder);

        if (rows <= 0) return 0;
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    /**
     * 왜: LM_COURSE_MODULE 배치 생성. courseModule.insert() 재현.
     *     module='homework'로 과목에 과제를 배치합니다.
     */
    public boolean insertCourseModule(int courseId, long siteId, long homeworkId,
                                       String title, int assignScore,
                                       String startDateTime, String endDateTime) {
        String sql = """
                INSERT INTO LM_COURSE_MODULE (course_id, site_id, module, module_id, module_nm,
                    parent_id, item_type, assign_score, apply_type,
                    start_day, period, start_date, end_date, chapter,
                    retry_yn, retry_score, retry_cnt, review_yn, result_yn, status)
                VALUES (:courseId, :siteId, 'homework', :homeworkId, :title,
                    0, 'R', :assignScore, '1',
                    0, 0, :startDate, :endDate, 0,
                    'N', 0, 0, 'N', 'Y', 1)
                """;
        int rows = jdbc.update(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("siteId", siteId)
                .addValue("homeworkId", homeworkId)
                .addValue("title", title)
                .addValue("assignScore", assignScore)
                .addValue("startDate", startDateTime)
                .addValue("endDate", endDateTime));
        return rows > 0;
    }

    // ==================== homework_modify.jsp ====================

    /**
     * 왜: LM_HOMEWORK 수정. homework.update("id = X AND site_id = Y AND status != -1") 재현.
     */
    public int updateHomework(int homeworkId, long siteId, String title, String content, String onoffType) {
        String sql = """
                UPDATE LM_HOMEWORK
                SET homework_nm = :title, onoff_type = :onoffType, content = :content
                WHERE id = :homeworkId AND site_id = :siteId AND status != -1
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("homeworkId", homeworkId)
                .addValue("siteId", siteId)
                .addValue("title", title)
                .addValue("content", content)
                .addValue("onoffType", onoffType));
    }

    /**
     * 왜: LM_COURSE_MODULE 수정. courseModule.update("course_id = X AND module = 'homework' AND module_id = Y AND status = 1") 재현.
     */
    public int updateCourseModule(int courseId, int homeworkId, String title, int assignScore, String endDateTime) {
        String sql = """
                UPDATE LM_COURSE_MODULE
                SET module_nm = :title, assign_score = :assignScore, apply_type = '1', end_date = :endDate
                WHERE course_id = :courseId AND module = 'homework'
                  AND module_id = :homeworkId AND status = 1
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("homeworkId", homeworkId)
                .addValue("title", title)
                .addValue("assignScore", assignScore)
                .addValue("endDate", endDateTime));
    }

    // ==================== homework_delete.jsp ====================

    /**
     * 왜: courseModule.delete("course_id = X AND module = 'homework' AND module_id = Y") 재현.
     *     물리 삭제입니다.
     */
    public boolean deleteCourseModule(int courseId, int homeworkId) {
        String sql = """
                DELETE FROM LM_COURSE_MODULE
                WHERE course_id = :courseId AND module = 'homework' AND module_id = :homeworkId
                """;
        int rows = jdbc.update(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId).addValue("homeworkId", homeworkId));
        return rows > 0;
    }

    /**
     * 왜: homework.item("status", -1); homework.update("id = X AND site_id = Y") 재현.
     *     소프트 삭제(status = -1)입니다.
     */
    public void softDeleteHomework(long homeworkId, long siteId) {
        String sql = """
                UPDATE LM_HOMEWORK SET status = -1
                WHERE id = :homeworkId AND site_id = :siteId
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("homeworkId", homeworkId).addValue("siteId", siteId));
    }

    // ==================== homework_feedback_update.jsp ====================

    /**
     * 왜: 과목에 배치된 과제의 assign_score를 조회합니다.
     *     courseModule.query("SELECT a.assign_score FROM ... WHERE module='homework'") 재현.
     */
    public Optional<Map<String, Object>> findCourseModuleForHomework(int courseId, int homeworkId, long siteId) {
        String sql = """
                SELECT a.assign_score
                FROM LM_COURSE_MODULE a
                INNER JOIN LM_HOMEWORK h ON a.module_id = h.id AND h.site_id = :siteId AND h.status != -1
                WHERE a.course_id = :courseId AND a.module = 'homework'
                  AND a.module_id = :homeworkId AND a.status = 1
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId).addValue("homeworkId", homeworkId)
                .addValue("siteId", siteId)).stream().findFirst();
    }

    /**
     * 왜: homeworkUser.find("homework_id = X AND course_user_id = Y AND status = 1") 재현.
     */
    public Optional<Map<String, Object>> findHomeworkUser(int homeworkId, int courseUserId) {
        String sql = """
                SELECT * FROM LM_HOMEWORK_USER
                WHERE homework_id = :homeworkId AND course_user_id = :courseUserId AND status = 1
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("homeworkId", homeworkId).addValue("courseUserId", courseUserId))
                .stream().findFirst();
    }

    /**
     * 왜: courseUserId로 user_id를 조회합니다. cuinfo.i("user_id") 재현.
     */
    public int findUserIdByCourseUserId(int courseUserId, long siteId) {
        String sql = """
                SELECT user_id FROM LM_COURSE_USER
                WHERE id = :courseUserId AND site_id = :siteId AND status IN (1, 3)
                """;
        try {
            Integer userId = jdbc.queryForObject(sql, new MapSqlParameterSource()
                    .addValue("courseUserId", courseUserId).addValue("siteId", siteId), Integer.class);
            return userId != null ? userId : 0;
        } catch (Exception e) {
            log.warn("수강 사용자 조회 실패: courseUserId={}, error={}", courseUserId, e.getMessage());
            return 0;
        }
    }

    /**
     * 왜: 기존 제출 레코드의 피드백/점수를 수정합니다.
     *     homeworkUser.update("homework_id = X AND course_user_id = Y") 재현.
     */
    public boolean updateHomeworkUserFeedback(int homeworkId, int courseUserId, long confirmUserId,
                                               double markingScore, double score,
                                               String feedback, String now) {
        String sql = """
                UPDATE LM_HOMEWORK_USER
                SET submit_yn = 'Y', confirm_yn = 'Y',
                    confirm_user_id = :confirmUserId, confirm_date = :now,
                    marking_score = :markingScore, score = :score,
                    feedback = :feedback, mod_date = :now
                WHERE homework_id = :homeworkId AND course_user_id = :courseUserId
                """;
        int rows = jdbc.update(sql, new MapSqlParameterSource()
                .addValue("homeworkId", homeworkId)
                .addValue("courseUserId", courseUserId)
                .addValue("confirmUserId", confirmUserId)
                .addValue("now", now)
                .addValue("markingScore", markingScore)
                .addValue("score", score)
                .addValue("feedback", feedback));
        return rows > 0;
    }

    /**
     * 왜: 오프라인 과제(또는 별도 제출경로)도 채점이 가능해야 하므로, 없으면 레코드를 생성합니다.
     *     homeworkUser.insert() 재현.
     */
    public boolean insertHomeworkUser(int homeworkId, int courseUserId, int courseId,
                                       int userId, long siteId, long confirmUserId,
                                       double markingScore, double score,
                                       String feedback, String now) {
        String sql = """
                INSERT INTO LM_HOMEWORK_USER (homework_id, course_user_id, course_id, user_id,
                    site_id, subject, content, user_file,
                    marking_score, score, feedback,
                    submit_yn, confirm_yn, confirm_user_id, confirm_date,
                    ip_addr, mod_date, reg_date, status)
                VALUES (:homeworkId, :courseUserId, :courseId, :userId,
                    :siteId, '', '', '',
                    :markingScore, :score, :feedback,
                    'Y', 'Y', :confirmUserId, :now,
                    '', :now, :now, 1)
                """;
        int rows = jdbc.update(sql, new MapSqlParameterSource()
                .addValue("homeworkId", homeworkId)
                .addValue("courseUserId", courseUserId)
                .addValue("courseId", courseId)
                .addValue("userId", userId)
                .addValue("siteId", siteId)
                .addValue("confirmUserId", confirmUserId)
                .addValue("markingScore", markingScore)
                .addValue("score", score)
                .addValue("feedback", feedback)
                .addValue("now", now));
        return rows > 0;
    }

    /**
     * 왜: courseUser.setCourseUserScore(courseUserId, "homework") 재현.
     *     해당 수강생의 과제 점수 합계를 LM_COURSE_USER.homework_score에 반영합니다.
     *     그 뒤 total_score = progress_score + exam_score + homework_score + forum_score + etc_score를 재계산합니다.
     */
    public void recalculateCourseUserHomeworkScore(int courseUserId, long siteId) {
        // 왜: 1단계 - 해당 수강생의 과제 점수 합계를 계산합니다.
        String calcSql = """
                SELECT IFNULL(SUM(hu.score), 0)
                FROM LM_HOMEWORK_USER hu
                WHERE hu.course_user_id = :courseUserId AND hu.status = 1
                  AND hu.confirm_yn = 'Y'
                """;
        Double hwScore = jdbc.queryForObject(calcSql, new MapSqlParameterSource()
                .addValue("courseUserId", courseUserId), Double.class);
        double homeworkScore = hwScore != null ? hwScore : 0.0;

        // 왜: 2단계 - LM_COURSE_USER에 과제 점수를 반영하고 total_score를 재계산합니다.
        String updateSql = """
                UPDATE LM_COURSE_USER
                SET homework_score = :homeworkScore,
                    total_score = IFNULL(progress_score, 0) + IFNULL(exam_score, 0)
                                + :homeworkScore + IFNULL(forum_score, 0) + IFNULL(etc_score, 0),
                    mod_date = NOW()
                WHERE id = :courseUserId AND site_id = :siteId
                """;
        jdbc.update(updateSql, new MapSqlParameterSource()
                .addValue("homeworkScore", homeworkScore)
                .addValue("courseUserId", courseUserId)
                .addValue("siteId", siteId));

        log.debug("성적 재계산 완료: courseUserId={}, homeworkScore={}", courseUserId, homeworkScore);
    }

    // ==================== homework_submissions.jsp ====================

    /**
     * 왜: homework_submissions.jsp의 COUNT 쿼리를 재현합니다.
     *     과목 구분 없이 교수자 담당 과목의 전체 제출 건수를 조회합니다.
     */
    public int countSubmissions(long userId, long siteId, boolean isAdmin,
                                 String keyword, String startDate, String endDate, String status) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM LM_HOMEWORK_USER hu
                INNER JOIN LM_COURSE_USER cu ON cu.id = hu.course_user_id AND cu.status IN (1, 3) AND cu.site_id = :siteId
                INNER JOIN TB_USER u ON u.id = cu.user_id AND u.status != -1
                INNER JOIN LM_COURSE c ON c.id = hu.course_id AND c.site_id = :siteId AND c.status != -1 AND c.onoff_type != 'P'
                """);

        MapSqlParameterSource params = new MapSqlParameterSource().addValue("siteId", siteId);

        // 왜: 교수자는 본인 담당 과목만 조회합니다.
        if (!isAdmin) {
            sql.append(" INNER JOIN LM_COURSE_TUTOR ct ON ct.course_id = c.id")
               .append("   AND ct.user_id = :userId AND ct.type = 'major' AND ct.site_id = :siteId ");
            params.addValue("userId", userId);
        }

        sql.append(" INNER JOIN LM_HOMEWORK h ON h.id = hu.homework_id AND h.site_id = :siteId AND h.status != -1 ");
        sql.append(" WHERE hu.status = 1 AND hu.submit_yn = 'Y' ");

        appendSubmissionFilters(sql, params, keyword, startDate, endDate, status);

        Integer count = jdbc.queryForObject(sql.toString(), params, Integer.class);
        return count != null ? count : 0;
    }

    /**
     * 왜: homework_submissions.jsp의 메인 목록 쿼리를 재현합니다.
     *     과목 구분 없이 통합 제출 목록을 페이지네이션하여 조회합니다.
     */
    public List<Map<String, Object>> listSubmissions(long userId, long siteId, boolean isAdmin,
                                                      String keyword, String startDate, String endDate,
                                                      String status, int offset, int pageSize) {
        StringBuilder sql = new StringBuilder("""
                SELECT hu.course_id, hu.homework_id, hu.course_user_id,
                       hu.reg_date AS submit_date, hu.confirm_yn,
                       c.course_nm,
                       h.homework_nm,
                       u.user_nm, u.login_id,
                       CASE WHEN IFNULL(c.etc2, '') = 'HAKSA_MAPPED' THEN 'haksa' ELSE 'prism' END AS source_type
                FROM LM_HOMEWORK_USER hu
                INNER JOIN LM_COURSE_USER cu ON cu.id = hu.course_user_id AND cu.status IN (1, 3) AND cu.site_id = :siteId
                INNER JOIN TB_USER u ON u.id = cu.user_id AND u.status != -1
                INNER JOIN LM_COURSE c ON c.id = hu.course_id AND c.site_id = :siteId AND c.status != -1 AND c.onoff_type != 'P'
                """);

        MapSqlParameterSource params = new MapSqlParameterSource().addValue("siteId", siteId);

        // 왜: 교수자는 본인 담당 과목만 조회합니다.
        if (!isAdmin) {
            sql.append(" INNER JOIN LM_COURSE_TUTOR ct ON ct.course_id = c.id")
               .append("   AND ct.user_id = :userId AND ct.type = 'major' AND ct.site_id = :siteId ");
            params.addValue("userId", userId);
        }

        sql.append(" INNER JOIN LM_HOMEWORK h ON h.id = hu.homework_id AND h.site_id = :siteId AND h.status != -1 ");
        sql.append(" WHERE hu.status = 1 AND hu.submit_yn = 'Y' ");

        appendSubmissionFilters(sql, params, keyword, startDate, endDate, status);

        // 왜: 미채점 건이 상단에 오도록 정렬합니다.
        sql.append(" ORDER BY (CASE WHEN hu.confirm_yn = 'Y' THEN 1 ELSE 0 END) ASC, hu.reg_date DESC ");
        sql.append(" LIMIT :offset, :pageSize ");
        params.addValue("offset", offset).addValue("pageSize", pageSize);

        return jdbc.queryForList(sql.toString(), params);
    }

    /**
     * 왜: homework_submissions.jsp의 keyword/date/status 필터 WHERE 절을 공통화합니다.
     */
    private void appendSubmissionFilters(StringBuilder sql, MapSqlParameterSource params,
                                          String keyword, String startDate, String endDate, String status) {
        if (keyword != null && !keyword.isEmpty()) {
            sql.append(" AND (h.homework_nm LIKE :keyword OR c.course_nm LIKE :keyword")
               .append("   OR u.user_nm LIKE :keyword OR u.login_id LIKE :keyword) ");
            params.addValue("keyword", "%" + keyword + "%");
        }
        if (startDate != null && !startDate.isEmpty()) {
            sql.append(" AND hu.reg_date >= :startDate ");
            params.addValue("startDate", startDate + "000000");
        }
        if (endDate != null && !endDate.isEmpty()) {
            sql.append(" AND hu.reg_date <= :endDate ");
            params.addValue("endDate", endDate + "235959");
        }
        if ("unconfirmed".equals(status)) {
            sql.append(" AND (hu.confirm_yn IS NULL OR hu.confirm_yn != 'Y') ");
        } else if ("confirmed".equals(status)) {
            sql.append(" AND hu.confirm_yn = 'Y' ");
        }
    }

    // ==================== homework_submit_cancel.jsp ====================

    /**
     * 왜: homeworkUser.delete("homework_id = X AND course_user_id = Y") 재현.
     *     물리 삭제입니다.
     */
    public boolean deleteHomeworkUser(int homeworkId, int courseUserId) {
        String sql = """
                DELETE FROM LM_HOMEWORK_USER
                WHERE homework_id = :homeworkId AND course_user_id = :courseUserId
                """;
        int rows = jdbc.update(sql, new MapSqlParameterSource()
                .addValue("homeworkId", homeworkId).addValue("courseUserId", courseUserId));
        return rows > 0;
    }

    /**
     * 왜: homeworkTask.delete("homework_id = X AND course_user_id = Y") 재현.
     *     추가과제를 함께 정리합니다.
     */
    public void deleteHomeworkTasks(int homeworkId, int courseUserId) {
        String sql = """
                DELETE FROM LM_HOMEWORK_TASK
                WHERE homework_id = :homeworkId AND course_user_id = :courseUserId
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("homeworkId", homeworkId).addValue("courseUserId", courseUserId));
    }

    /**
     * 왜: file.execute("DELETE FROM CL_FILE WHERE module = 'homework_X' AND module_id = Y") 재현.
     *     첨부파일을 함께 정리합니다.
     */
    public void deleteHomeworkFiles(int homeworkId, int courseUserId) {
        // 왜: 과제 첨부파일과 피드백 첨부파일 모두 삭제합니다.
        String sql1 = """
                DELETE FROM CL_FILE
                WHERE module = :module AND module_id = :moduleId
                """;
        jdbc.update(sql1, new MapSqlParameterSource()
                .addValue("module", "homework_" + homeworkId)
                .addValue("moduleId", courseUserId));

        String sql2 = """
                DELETE FROM CL_FILE
                WHERE module = :module AND module_id = :moduleId
                """;
        jdbc.update(sql2, new MapSqlParameterSource()
                .addValue("module", "homework_feedback_" + homeworkId)
                .addValue("moduleId", courseUserId));
    }

    // ==================== homework_task_append.jsp ====================

    /**
     * 왜: parent_id는 "직전 추가과제"를 가리키게 해서 타임라인을 만들 수 있게 합니다.
     *     homeworkTask.getOneInt("SELECT MAX(id) FROM ... WHERE status = 1") 재현.
     */
    public int findLastTaskId(long siteId, int courseId, int homeworkId, int courseUserId) {
        String sql = """
                SELECT MAX(id) FROM LM_HOMEWORK_TASK
                WHERE site_id = :siteId AND course_id = :courseId
                  AND homework_id = :homeworkId AND course_user_id = :courseUserId
                  AND status = 1
                """;
        try {
            Integer lastId = jdbc.queryForObject(sql, new MapSqlParameterSource()
                    .addValue("siteId", siteId).addValue("courseId", courseId)
                    .addValue("homeworkId", homeworkId).addValue("courseUserId", courseUserId), Integer.class);
            return lastId != null && lastId > 0 ? lastId : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 왜: LM_HOMEWORK_TASK 신규 생성. homeworkTask.getSequence() + homeworkTask.insert() 재현.
     */
    public long insertHomeworkTask(long siteId, int courseId, int homeworkId,
                                    int courseUserId, int studentUserId, int parentId,
                                    long assignUserId, String task, String now) {
        String sql = """
                INSERT INTO LM_HOMEWORK_TASK (site_id, course_id, homework_id, course_user_id,
                    user_id, parent_id, assign_user_id, task, subject, content,
                    submit_yn, submit_date, confirm_yn, confirm_user_id, confirm_date,
                    feedback, ip_addr, mod_date, reg_date, status)
                VALUES (:siteId, :courseId, :homeworkId, :courseUserId,
                    :userId, :parentId, :assignUserId, :task, '', '',
                    'N', '', 'N', 0, '',
                    '', '', :now, :now, 1)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int rows = jdbc.update(sql, new MapSqlParameterSource()
                .addValue("siteId", siteId)
                .addValue("courseId", courseId)
                .addValue("homeworkId", homeworkId)
                .addValue("courseUserId", courseUserId)
                .addValue("userId", studentUserId)
                .addValue("parentId", parentId)
                .addValue("assignUserId", assignUserId)
                .addValue("task", task)
                .addValue("now", now), keyHolder);

        if (rows <= 0) return 0;
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    // ==================== homework_task_confirm.jsp ====================

    /**
     * 왜: homeworkTask.update("id = X") 재현. 교수자 확인(평가완료) 처리.
     */
    public boolean confirmHomeworkTask(int taskId, long confirmUserId, String feedback, String now) {
        String sql = """
                UPDATE LM_HOMEWORK_TASK
                SET confirm_yn = 'Y', confirm_user_id = :confirmUserId,
                    confirm_date = :now, feedback = :feedback, mod_date = :now
                WHERE id = :taskId
                """;
        int rows = jdbc.update(sql, new MapSqlParameterSource()
                .addValue("taskId", taskId)
                .addValue("confirmUserId", confirmUserId)
                .addValue("feedback", feedback)
                .addValue("now", now));
        return rows > 0;
    }

    // ==================== homework_task_list.jsp ====================

    /**
     * 왜: homeworkTask.find("site_id = X AND course_id = Y AND homework_id = Z AND course_user_id = W AND status = 1") 재현.
     */
    public List<Map<String, Object>> listHomeworkTasks(long siteId, int courseId, int homeworkId, int courseUserId) {
        String sql = """
                SELECT id, task, subject, content, submit_yn, submit_date,
                       confirm_yn, confirm_date, feedback, reg_date
                FROM LM_HOMEWORK_TASK
                WHERE site_id = :siteId AND course_id = :courseId
                  AND homework_id = :homeworkId AND course_user_id = :courseUserId
                  AND status = 1
                ORDER BY id DESC
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("siteId", siteId).addValue("courseId", courseId)
                .addValue("homeworkId", homeworkId).addValue("courseUserId", courseUserId));
    }

    // ==================== homework_users.jsp ====================

    /**
     * 왜: homework_users.jsp의 과제+배치 정보 조회를 재현합니다.
     *     assign_score, start_date, end_date, homework_nm, onoff_type를 함께 반환합니다.
     */
    public Optional<Map<String, Object>> findCourseModuleWithHomeworkInfo(int courseId, int homeworkId, long siteId) {
        String sql = """
                SELECT a.assign_score, a.start_date, a.end_date,
                       h.homework_nm, h.onoff_type
                FROM LM_COURSE_MODULE a
                INNER JOIN LM_HOMEWORK h ON a.module_id = h.id AND h.site_id = :siteId AND h.status != -1
                WHERE a.course_id = :courseId AND a.module = 'homework'
                  AND a.module_id = :homeworkId AND a.status = 1
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId).addValue("homeworkId", homeworkId)
                .addValue("siteId", siteId)).stream().findFirst();
    }

    /**
     * 왜: homework_users.jsp의 수강생별 제출/채점 현황 조회를 재현합니다.
     *     LM_COURSE_USER x TB_USER LEFT JOIN LM_HOMEWORK_USER 구조.
     */
    public List<Map<String, Object>> listHomeworkUsers(int courseId, int homeworkId, long siteId) {
        String sql = """
                SELECT cu.id AS course_user_id, cu.user_id,
                       u.login_id, u.user_nm,
                       hu.submit_yn, hu.reg_date AS submit_date,
                       hu.confirm_yn, hu.confirm_date,
                       hu.marking_score, hu.score, hu.subject, hu.feedback,
                       (SELECT COUNT(*) FROM LM_HOMEWORK_TASK ht
                        WHERE ht.site_id = :siteId AND ht.course_id = :courseId
                          AND ht.homework_id = :homeworkId AND ht.course_user_id = cu.id
                          AND ht.status = 1) AS task_cnt
                FROM LM_COURSE_USER cu
                INNER JOIN TB_USER u ON u.id = cu.user_id AND u.status != -1
                LEFT JOIN LM_HOMEWORK_USER hu ON hu.course_user_id = cu.id
                    AND hu.homework_id = :homeworkId AND hu.status = 1
                WHERE cu.site_id = :siteId AND cu.course_id = :courseId
                  AND cu.status IN (1, 3)
                ORDER BY u.user_nm ASC, cu.id ASC
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("siteId", siteId).addValue("courseId", courseId)
                .addValue("homeworkId", homeworkId));
    }
}
