package kr.go.growailms.tutor.exam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * 왜: 레거시 DAO(ExamDao, ExamUserDao, CourseModuleDao 등)가 하던 DB 접근을
 *     Spring Boot의 NamedParameterJdbcTemplate으로 대체합니다.
 *     테이블/컬럼명은 레거시와 동일하게 유지하여 데이터 호환성을 보장합니다.
 */
@Repository
public class TutorExamJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(TutorExamJdbcRepository.class);
    private final NamedParameterJdbcTemplate jdbc;

    public TutorExamJdbcRepository(NamedParameterJdbcTemplate jdbc) {
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

    // ==================== 과목 존재 확인 ====================

    /**
     * 왜: 시험 CRUD 전에 과목이 유효한지 확인해야 합니다.
     */
    public boolean courseExists(int courseId, long siteId) {
        String sql = """
                SELECT COUNT(*) FROM LM_COURSE
                WHERE id = :courseId AND site_id = :siteId AND status != -1
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("siteId", siteId);
        Integer count = jdbc.queryForObject(sql, params, Integer.class);
        return count != null && count > 0;
    }

    // ==================== exam_list.jsp ====================

    /**
     * 왜: exam_list.jsp의 메인 쿼리를 재현합니다.
     *     LM_COURSE_MODULE(배치) + LM_EXAM(시험) JOIN으로 과목에 배치된 시험 목록을 조회하고,
     *     수강생 수/제출 수/채점 수를 서브쿼리로 함께 가져옵니다.
     */
    public List<Map<String, Object>> listExamsByCourse(int courseId, long siteId) {
        String sql = """
                SELECT a.module_id AS exam_id, a.module_nm, a.apply_type,
                       a.start_date, a.end_date, a.chapter,
                       a.assign_score, a.result_yn, a.retry_yn, a.retry_cnt,
                       e.exam_nm, e.exam_time, e.question_cnt, e.onoff_type,
                       (SELECT COUNT(*) FROM LM_COURSE_USER cu
                        WHERE cu.site_id = :siteId AND cu.course_id = a.course_id
                          AND cu.status IN (1,3)) AS total_cnt,
                       (SELECT COUNT(*) FROM LM_EXAM_USER eu
                        INNER JOIN LM_COURSE_USER cu ON cu.id = eu.course_user_id AND cu.status IN (1,3)
                        WHERE eu.exam_id = a.module_id AND eu.course_id = a.course_id
                          AND eu.exam_step = 1 AND eu.status = 1 AND eu.submit_yn = 'Y') AS submitted_cnt,
                       (SELECT COUNT(*) FROM LM_EXAM_USER eu
                        INNER JOIN LM_COURSE_USER cu ON cu.id = eu.course_user_id AND cu.status IN (1,3)
                        WHERE eu.exam_id = a.module_id AND eu.course_id = a.course_id
                          AND eu.exam_step = 1 AND eu.status = 1 AND eu.submit_yn = 'Y'
                          AND eu.confirm_yn = 'Y') AS confirmed_cnt
                FROM LM_COURSE_MODULE a
                INNER JOIN LM_EXAM e ON a.module_id = e.id AND e.site_id = :siteId AND e.status != -1
                WHERE a.course_id = :courseId AND a.module = 'exam' AND a.status = 1
                ORDER BY a.start_date ASC, a.end_date ASC, a.period ASC, a.chapter ASC, a.module_id ASC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("siteId", siteId);
        return jdbc.queryForList(sql, params);
    }

    // ==================== exam_insert.jsp ====================

    /**
     * 왜: LM_EXAM 테이블에 시험 레코드를 생성합니다.
     *     문제은행/난이도 분배는 tutor 화면에서 아직 다루지 않으므로 최소값으로 채웁니다.
     */
    public long insertExam(long siteId, String title, int examTime, String description,
                           int questionCnt, String retakeYn, String onoffType, long managerId) {
        String sql = """
                INSERT INTO LM_EXAM (site_id, category_id, onoff_type, exam_nm, range_idx,
                    exam_time, content, question_cnt,
                    mcnt1, mcnt2, mcnt3, mcnt4, mcnt5, mcnt6,
                    tcnt1, tcnt2, tcnt3, tcnt4, tcnt5, tcnt6,
                    assign1, assign2, assign3, assign4, assign5, assign6,
                    shuffle_yn, auto_complete_yn, retake_yn, permission_number,
                    manager_id, reg_date, status)
                VALUES (:siteId, 0, :onoffType, :title, '',
                    :examTime, :description, :questionCnt,
                    :questionCnt, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0,
                    :assign1, 0, 0, 0, 0, 0,
                    'Y', 'N', :retakeYn, :permissionNumber,
                    :managerId, NOW(), 1)
                """;
        // 왜: assign1은 문제 수가 0이 아닐 때 100/문제수로 기본 배점을 계산합니다.
        int assign1 = questionCnt > 0 ? Math.max(1, 100 / questionCnt) : 0;
        int permissionNumber = "Y".equals(retakeYn) ? 1 : 0;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", siteId)
                .addValue("onoffType", onoffType)
                .addValue("title", title)
                .addValue("examTime", examTime)
                .addValue("description", description)
                .addValue("questionCnt", questionCnt)
                .addValue("assign1", assign1)
                .addValue("retakeYn", retakeYn)
                .addValue("permissionNumber", permissionNumber)
                .addValue("managerId", managerId);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    /**
     * 왜: LM_COURSE_MODULE 테이블에 시험 배치를 생성합니다.
     *     exam_insert.jsp에서 시험 생성 후 바로 과목에 배치하는 로직을 재현합니다.
     */
    public int insertCourseModule(int courseId, long siteId, long examId, String title,
                                  int assignScore, String startDate, String endDate,
                                  String retakeYn, String resultYn) {
        String sql = """
                INSERT INTO LM_COURSE_MODULE (course_id, site_id, module, module_id, module_nm,
                    parent_id, item_type, assign_score, apply_type, start_day, period,
                    start_date, end_date, chapter, retry_yn, retry_score, retry_cnt,
                    review_yn, result_yn, status)
                VALUES (:courseId, :siteId, 'exam', :examId, :title,
                    0, 'R', :assignScore, '1', 0, 0,
                    :startDate, :endDate, 0, :retakeYn, 0, :retryCnt,
                    'N', :resultYn, 1)
                """;
        int retryCnt = "Y".equals(retakeYn) ? 1 : 0;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("siteId", siteId)
                .addValue("examId", examId)
                .addValue("title", title)
                .addValue("assignScore", assignScore)
                .addValue("startDate", startDate)
                .addValue("endDate", endDate)
                .addValue("retakeYn", retakeYn)
                .addValue("retryCnt", retryCnt)
                .addValue("resultYn", resultYn);
        return jdbc.update(sql, params);
    }

    /**
     * 왜: 시험은 생성됐는데 과목 배치가 실패하면 "유령 시험"이 생기므로 soft-delete합니다.
     */
    public void softDeleteExam(long examId, long siteId) {
        String sql = """
                UPDATE LM_EXAM SET status = -1
                WHERE id = :examId AND site_id = :siteId
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("examId", examId)
                .addValue("siteId", siteId));
    }

    // ==================== exam_modify.jsp ====================

    /**
     * 왜: 과목에 배치된 시험의 존재 여부를 확인합니다.
     */
    public Optional<Map<String, Object>> findCourseModule(int courseId, int examId) {
        String sql = """
                SELECT * FROM LM_COURSE_MODULE
                WHERE course_id = :courseId AND module = 'exam' AND module_id = :examId AND status = 1
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("examId", examId);
        return jdbc.queryForList(sql, params).stream().findFirst();
    }

    /**
     * 왜: 시험 정보를 조회합니다.
     */
    public Optional<Map<String, Object>> findExam(int examId, long siteId) {
        String sql = """
                SELECT * FROM LM_EXAM
                WHERE id = :examId AND site_id = :siteId AND status != -1
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("examId", examId)
                .addValue("siteId", siteId);
        return jdbc.queryForList(sql, params).stream().findFirst();
    }

    /**
     * 왜: LM_EXAM 테이블의 시험 정보를 수정합니다.
     */
    public int updateExam(int examId, long siteId, String title, int examTime,
                          String description, int questionCnt, String retakeYn, String onoffType) {
        String sql = """
                UPDATE LM_EXAM SET
                    exam_nm = :title, onoff_type = :onoffType,
                    exam_time = :examTime, content = :description,
                    question_cnt = :questionCnt, retake_yn = :retakeYn,
                    permission_number = :permissionNumber
                WHERE id = :examId AND site_id = :siteId AND status != -1
                """;
        int permissionNumber = "Y".equals(retakeYn) ? 1 : 0;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("examId", examId)
                .addValue("siteId", siteId)
                .addValue("title", title)
                .addValue("onoffType", onoffType)
                .addValue("examTime", examTime)
                .addValue("description", description)
                .addValue("questionCnt", questionCnt)
                .addValue("retakeYn", retakeYn)
                .addValue("permissionNumber", permissionNumber);
        return jdbc.update(sql, params);
    }

    /**
     * 왜: LM_COURSE_MODULE 테이블의 과목 배치 정보를 수정합니다.
     */
    public int updateCourseModule(int courseId, int examId, String title,
                                  int assignScore, String startDate, String endDate,
                                  String retakeYn, String resultYn) {
        String sql = """
                UPDATE LM_COURSE_MODULE SET
                    module_nm = :title, assign_score = :assignScore,
                    apply_type = '1', start_day = 0, period = 0,
                    start_date = :startDate, end_date = :endDate, chapter = 0,
                    retry_yn = :retakeYn, retry_score = 0, retry_cnt = :retryCnt,
                    result_yn = :resultYn
                WHERE course_id = :courseId AND module = 'exam' AND module_id = :examId AND status = 1
                """;
        int retryCnt = "Y".equals(retakeYn) ? 1 : 0;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("examId", examId)
                .addValue("title", title)
                .addValue("assignScore", assignScore)
                .addValue("startDate", startDate)
                .addValue("endDate", endDate)
                .addValue("retakeYn", retakeYn)
                .addValue("retryCnt", retryCnt)
                .addValue("resultYn", resultYn);
        return jdbc.update(sql, params);
    }

    // ==================== exam_delete.jsp ====================

    /**
     * 왜: 응시/채점 내역이 있으면 배치만 지워도 데이터가 끊기므로 삭제를 막습니다.
     */
    public boolean hasSubmissions(int examId, int courseId) {
        String sql = """
                SELECT COUNT(*) FROM LM_EXAM_USER
                WHERE exam_id = :examId AND course_id = :courseId AND status = 1
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("examId", examId)
                .addValue("courseId", courseId);
        Integer count = jdbc.queryForObject(sql, params, Integer.class);
        return count != null && count > 0;
    }

    /**
     * 왜: 과목 배치(LM_COURSE_MODULE)를 삭제합니다.
     */
    public int deleteCourseModule(int courseId, int examId) {
        String sql = """
                DELETE FROM LM_COURSE_MODULE
                WHERE course_id = :courseId AND module = 'exam' AND module_id = :examId
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("examId", examId));
    }

    /**
     * 왜: 다른 과목에서 쓰지 않는 시험이면 soft-delete로 정리합니다.
     */
    public boolean isExamUsedElsewhere(int examId) {
        String sql = """
                SELECT COUNT(*) FROM LM_COURSE_MODULE
                WHERE module = 'exam' AND module_id = :examId
                """;
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource("examId", examId), Integer.class);
        return count != null && count > 0;
    }

    // ==================== exam_link.jsp ====================

    /**
     * 왜: 이미 과목에 연결된 시험인지 확인합니다. 중복 연결을 방지합니다.
     */
    public boolean isExamLinked(int courseId, int examId) {
        String sql = """
                SELECT COUNT(*) FROM LM_COURSE_MODULE
                WHERE course_id = :courseId AND module = 'exam' AND module_id = :examId AND status = 1
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("examId", examId);
        Integer count = jdbc.queryForObject(sql, params, Integer.class);
        return count != null && count > 0;
    }

    // ==================== exam_score_update.jsp ====================

    /**
     * 왜: 시험 배치 정보에서 assign_score(배점)를 조회합니다.
     *     채점 시 marking_score(%)를 실제 점수로 변환하는 데 사용됩니다.
     */
    public Optional<Map<String, Object>> findCourseModuleWithExam(int courseId, int examId, long siteId) {
        String sql = """
                SELECT a.assign_score
                FROM LM_COURSE_MODULE a
                INNER JOIN LM_EXAM e ON a.module_id = e.id AND e.site_id = :siteId AND e.status != -1
                WHERE a.course_id = :courseId AND a.module = 'exam' AND a.module_id = :examId AND a.status = 1
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("examId", examId)
                .addValue("siteId", siteId);
        return jdbc.queryForList(sql, params).stream().findFirst();
    }

    /**
     * 왜: 수강 정보가 유효한지 확인합니다.
     */
    public Optional<Map<String, Object>> findCourseUser(int courseUserId, int courseId, long siteId) {
        String sql = """
                SELECT id, user_id, course_id FROM LM_COURSE_USER
                WHERE id = :courseUserId AND course_id = :courseId
                  AND site_id = :siteId AND status IN (1,3)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseUserId", courseUserId)
                .addValue("courseId", courseId)
                .addValue("siteId", siteId);
        return jdbc.queryForList(sql, params).stream().findFirst();
    }

    /**
     * 왜: 기존 응시 레코드가 있는지 확인합니다.
     */
    public Optional<Map<String, Object>> findExamUser(int examId, int courseUserId) {
        String sql = """
                SELECT * FROM LM_EXAM_USER
                WHERE exam_id = :examId AND course_user_id = :courseUserId
                  AND exam_step = 1 AND status = 1
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("examId", examId)
                .addValue("courseUserId", courseUserId);
        return jdbc.queryForList(sql, params).stream().findFirst();
    }

    /**
     * 왜: 기존 응시 레코드를 업데이트합니다(점수, 채점 완료 표시).
     */
    public int updateExamUserScore(int examId, int courseUserId, double markingScore,
                                   double score, long confirmUserId, String now) {
        // 왜: submit_date가 비어있으면 현재 시각으로 채워줍니다.
        String sql = """
                UPDATE LM_EXAM_USER SET
                    submit_yn = 'Y',
                    submit_date = CASE WHEN submit_date IS NULL OR submit_date = '' THEN :now ELSE submit_date END,
                    confirm_yn = 'Y', confirm_user_id = :confirmUserId, confirm_date = :now,
                    marking_score = :markingScore, score = :score, mod_date = :now
                WHERE exam_id = :examId AND course_user_id = :courseUserId AND exam_step = 1
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("examId", examId)
                .addValue("courseUserId", courseUserId)
                .addValue("markingScore", markingScore)
                .addValue("score", score)
                .addValue("confirmUserId", confirmUserId)
                .addValue("now", now);
        return jdbc.update(sql, params);
    }

    /**
     * 왜: 오프라인 시험은 학생이 온라인으로 응시하지 않을 수 있으므로,
     *     채점 시점에 응시 레코드를 자동으로 만들어 줍니다.
     */
    public int insertExamUser(int examId, int courseUserId, int courseId,
                              long userId, long siteId, double markingScore,
                              double score, long confirmUserId, String now, String ipAddr) {
        String sql = """
                INSERT INTO LM_EXAM_USER (
                    exam_id, course_user_id, exam_step, course_id, user_id, site_id,
                    choice_yn, score, marking_score, feedback, duration, ba_cnt,
                    submit_yn, confirm_yn, confirm_user_id, confirm_date,
                    submit_date, apply_cnt, apply_date, onload_date, unload_date,
                    ip_addr, mod_date, reg_date, status)
                VALUES (
                    :examId, :courseUserId, 1, :courseId, :userId, :siteId,
                    'Y', :score, :markingScore, '', 0, 0,
                    'Y', 'Y', :confirmUserId, :now,
                    :now, 1, :now, :now, :now,
                    :ipAddr, :now, :now, 1)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("examId", examId)
                .addValue("courseUserId", courseUserId)
                .addValue("courseId", courseId)
                .addValue("userId", userId)
                .addValue("siteId", siteId)
                .addValue("markingScore", markingScore)
                .addValue("score", score)
                .addValue("confirmUserId", confirmUserId)
                .addValue("now", now)
                .addValue("ipAddr", ipAddr);
        return jdbc.update(sql, params);
    }

    /**
     * 왜: 시험 점수 저장 후 과목 성적(LM_COURSE_USER.exam_score)을 재계산합니다.
     *     레거시의 courseUser.setCourseUserScore(courseUserId, "exam") 로직을 재현합니다.
     *     과목에 배치된 모든 시험의 최고점을 합산하여 반영합니다.
     */
    public void recalcCourseExamScore(int courseUserId) {
        // 왜: 해당 수강생의 과목에 배치된 모든 시험 점수를 합산합니다.
        //     exam_step=1(본시험)의 최고 score를 시험별로 가져와 합산합니다.
        String sql = """
                UPDATE LM_COURSE_USER cu SET
                    exam_score = COALESCE((
                        SELECT SUM(max_score) FROM (
                            SELECT eu.exam_id, MAX(eu.score) AS max_score
                            FROM LM_EXAM_USER eu
                            WHERE eu.course_user_id = :courseUserId
                              AND eu.exam_step = 1 AND eu.status = 1
                              AND eu.submit_yn = 'Y' AND eu.confirm_yn = 'Y'
                            GROUP BY eu.exam_id
                        ) t
                    ), 0)
                WHERE cu.id = :courseUserId
                """;
        jdbc.update(sql, new MapSqlParameterSource("courseUserId", courseUserId));
    }

    // ==================== exam_submit_cancel.jsp ====================

    /**
     * 왜: 응시 결과(LM_EXAM_RESULT)를 삭제합니다.
     */
    public int deleteExamResults(int examId, int courseUserId) {
        String sql = """
                DELETE FROM LM_EXAM_RESULT
                WHERE exam_id = :examId AND course_user_id = :courseUserId
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("examId", examId)
                .addValue("courseUserId", courseUserId));
    }

    /**
     * 왜: 응시 레코드(LM_EXAM_USER)를 삭제합니다.
     */
    public int deleteExamUser(int examId, int courseUserId) {
        String sql = """
                DELETE FROM LM_EXAM_USER
                WHERE exam_id = :examId AND course_user_id = :courseUserId AND exam_step = 1
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("examId", examId)
                .addValue("courseUserId", courseUserId));
    }

    // ==================== exam_users.jsp ====================

    /**
     * 왜: 시험 배치 정보 + 시험 기본 정보를 함께 조회합니다.
     *     exam_users.jsp에서 minfo로 사용하는 데이터입니다.
     */
    public Optional<Map<String, Object>> findCourseModuleDetail(int courseId, int examId, long siteId) {
        String sql = """
                SELECT a.assign_score, a.start_date, a.end_date,
                       a.retry_yn, a.retry_cnt, a.result_yn,
                       e.exam_nm, e.exam_time, e.onoff_type
                FROM LM_COURSE_MODULE a
                INNER JOIN LM_EXAM e ON a.module_id = e.id AND e.site_id = :siteId AND e.status != -1
                WHERE a.course_id = :courseId AND a.module = 'exam' AND a.module_id = :examId AND a.status = 1
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("examId", examId)
                .addValue("siteId", siteId);
        return jdbc.queryForList(sql, params).stream().findFirst();
    }

    /**
     * 왜: 수강생별 시험 제출/점수 정보를 조회합니다.
     *     exam_users.jsp의 메인 쿼리를 재현합니다.
     */
    public List<Map<String, Object>> listExamUsers(int courseId, int examId, long siteId) {
        String sql = """
                SELECT cu.id AS course_user_id, cu.user_id,
                       u.login_id, u.user_nm,
                       eu.submit_yn, eu.submit_date, eu.confirm_yn, eu.confirm_date,
                       eu.marking_score, eu.score
                FROM LM_COURSE_USER cu
                INNER JOIN TB_USER u ON u.id = cu.user_id AND u.status != -1
                LEFT JOIN LM_EXAM_USER eu ON eu.course_user_id = cu.id
                    AND eu.exam_id = :examId AND eu.exam_step = 1 AND eu.status = 1
                WHERE cu.site_id = :siteId AND cu.course_id = :courseId AND cu.status IN (1,3)
                ORDER BY u.user_nm ASC, cu.id ASC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("examId", examId)
                .addValue("siteId", siteId);
        return jdbc.queryForList(sql, params);
    }

    // ==================== exam_template_list.jsp ====================

    /**
     * 왜: 시험 템플릿 목록을 조회합니다.
     *     교수자는 본인 시험만, 관리자는 전체를 조회합니다.
     */
    public List<Map<String, Object>> listExamTemplates(long siteId, boolean isAdmin, long userId,
                                                       int page, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM LM_EXAM
                WHERE site_id = :siteId AND status != -1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", siteId);

        // 왜: 교수자는 본인이 등록한 시험만 조회합니다.
        if (!isAdmin) {
            sql.append(" AND manager_id = :userId ");
            params.addValue("userId", userId);
        }

        sql.append(" ORDER BY id DESC ");

        // 왜: 페이지네이션 적용
        int offset = (page - 1) * limit;
        sql.append(" LIMIT :limit OFFSET :offset ");
        params.addValue("limit", limit)
              .addValue("offset", offset);

        return jdbc.queryForList(sql.toString(), params);
    }

    /**
     * 왜: 시험 템플릿 전체 건수를 조회합니다 (페이지네이션용).
     */
    public int countExamTemplates(long siteId, boolean isAdmin, long userId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) FROM LM_EXAM
                WHERE site_id = :siteId AND status != -1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", siteId);

        if (!isAdmin) {
            sql.append(" AND manager_id = :userId ");
            params.addValue("userId", userId);
        }

        Integer count = jdbc.queryForObject(sql.toString(), params, Integer.class);
        return count != null ? count : 0;
    }

    // ==================== exam_template_insert.jsp ====================

    /**
     * 왜: 문제 ID 목록으로 난이도별 객관식/주관식 문항수를 계산합니다.
     *     시험 저장 시 난이도별 문항수/배점이 필수라서 문제 목록으로 직접 계산합니다.
     */
    public List<Map<String, Object>> findQuestionsByIds(List<Integer> questionIds, long siteId) {
        if (questionIds.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT id, grade, question_type FROM LM_QUESTION
                WHERE site_id = :siteId AND status != -1 AND id IN (:ids)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", siteId)
                .addValue("ids", questionIds);
        return jdbc.queryForList(sql, params);
    }

    /**
     * 왜: 시험 템플릿을 등록합니다 (온라인 시험 - 문제은행 연동).
     */
    public long insertExamTemplate(long siteId, String examName, int examTime,
                                   String shuffleYn, String content, String rangeIdx,
                                   int questionCnt, int[] mcnt, int[] tcnt, int[] assigns,
                                   long managerId) {
        String sql = """
                INSERT INTO LM_EXAM (site_id, onoff_type, exam_nm, exam_time,
                    shuffle_yn, auto_complete_yn, retake_yn, permission_number,
                    content, range_idx, question_cnt,
                    mcnt1, mcnt2, mcnt3, mcnt4, mcnt5, mcnt6,
                    tcnt1, tcnt2, tcnt3, tcnt4, tcnt5, tcnt6,
                    assign1, assign2, assign3, assign4, assign5, assign6,
                    manager_id, reg_date, status)
                VALUES (:siteId, 'N', :examName, :examTime,
                    :shuffleYn, 'Y', 'N', 0,
                    :content, :rangeIdx, :questionCnt,
                    :mcnt1, :mcnt2, :mcnt3, :mcnt4, :mcnt5, :mcnt6,
                    :tcnt1, :tcnt2, :tcnt3, :tcnt4, :tcnt5, :tcnt6,
                    :assign1, :assign2, :assign3, :assign4, :assign5, :assign6,
                    :managerId, NOW(), 1)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", siteId)
                .addValue("examName", examName)
                .addValue("examTime", examTime)
                .addValue("shuffleYn", shuffleYn)
                .addValue("content", content)
                .addValue("rangeIdx", rangeIdx)
                .addValue("questionCnt", questionCnt)
                .addValue("mcnt1", mcnt[1]).addValue("mcnt2", mcnt[2]).addValue("mcnt3", mcnt[3])
                .addValue("mcnt4", mcnt[4]).addValue("mcnt5", mcnt[5]).addValue("mcnt6", mcnt[6])
                .addValue("tcnt1", tcnt[1]).addValue("tcnt2", tcnt[2]).addValue("tcnt3", tcnt[3])
                .addValue("tcnt4", tcnt[4]).addValue("tcnt5", tcnt[5]).addValue("tcnt6", tcnt[6])
                .addValue("assign1", assigns[1]).addValue("assign2", assigns[2]).addValue("assign3", assigns[3])
                .addValue("assign4", assigns[4]).addValue("assign5", assigns[5]).addValue("assign6", assigns[6])
                .addValue("managerId", managerId);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    // ==================== exam_template_modify.jsp ====================

    /**
     * 왜: 시험 템플릿을 수정합니다.
     */
    public int updateExamTemplate(int examId, long siteId, String examName, int examTime,
                                  String shuffleYn, String content, String rangeIdx,
                                  int questionCnt, int[] mcnt, int[] tcnt, int[] assigns) {
        StringBuilder sql = new StringBuilder("UPDATE LM_EXAM SET ");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("examId", examId)
                .addValue("siteId", siteId);

        // 왜: JSP에서 빈 값이 아닐 때만 업데이트하는 로직을 재현합니다.
        List<String> setClauses = new ArrayList<>();
        if (examName != null && !examName.isEmpty()) {
            setClauses.add("exam_nm = :examName");
            params.addValue("examName", examName);
        }
        if (examTime > 0) {
            setClauses.add("exam_time = :examTime");
            params.addValue("examTime", examTime);
        }
        if (shuffleYn != null && !shuffleYn.isEmpty()) {
            setClauses.add("shuffle_yn = :shuffleYn");
            params.addValue("shuffleYn", "Y".equals(shuffleYn) ? "Y" : "N");
        }

        setClauses.add("content = :content");
        params.addValue("content", content != null ? content : "");

        setClauses.add("range_idx = :rangeIdx");
        params.addValue("rangeIdx", rangeIdx != null ? rangeIdx : "");

        setClauses.add("question_cnt = :questionCnt");
        params.addValue("questionCnt", questionCnt);

        setClauses.add("mcnt1 = :mcnt1"); params.addValue("mcnt1", mcnt[1]);
        setClauses.add("mcnt2 = :mcnt2"); params.addValue("mcnt2", mcnt[2]);
        setClauses.add("mcnt3 = :mcnt3"); params.addValue("mcnt3", mcnt[3]);
        setClauses.add("mcnt4 = :mcnt4"); params.addValue("mcnt4", mcnt[4]);
        setClauses.add("mcnt5 = :mcnt5"); params.addValue("mcnt5", mcnt[5]);
        setClauses.add("mcnt6 = :mcnt6"); params.addValue("mcnt6", mcnt[6]);
        setClauses.add("tcnt1 = :tcnt1"); params.addValue("tcnt1", tcnt[1]);
        setClauses.add("tcnt2 = :tcnt2"); params.addValue("tcnt2", tcnt[2]);
        setClauses.add("tcnt3 = :tcnt3"); params.addValue("tcnt3", tcnt[3]);
        setClauses.add("tcnt4 = :tcnt4"); params.addValue("tcnt4", tcnt[4]);
        setClauses.add("tcnt5 = :tcnt5"); params.addValue("tcnt5", tcnt[5]);
        setClauses.add("tcnt6 = :tcnt6"); params.addValue("tcnt6", tcnt[6]);
        setClauses.add("assign1 = :assign1"); params.addValue("assign1", assigns[1]);
        setClauses.add("assign2 = :assign2"); params.addValue("assign2", assigns[2]);
        setClauses.add("assign3 = :assign3"); params.addValue("assign3", assigns[3]);
        setClauses.add("assign4 = :assign4"); params.addValue("assign4", assigns[4]);
        setClauses.add("assign5 = :assign5"); params.addValue("assign5", assigns[5]);
        setClauses.add("assign6 = :assign6"); params.addValue("assign6", assigns[6]);

        sql.append(String.join(", ", setClauses));
        sql.append(" WHERE id = :examId AND site_id = :siteId ");

        return jdbc.update(sql.toString(), params);
    }

    // ==================== exam_template_delete.jsp ====================

    /**
     * 왜: 시험 템플릿을 소프트 삭제합니다 (status = -1).
     */
    public int softDeleteExamTemplate(int examId, long siteId) {
        String sql = """
                UPDATE LM_EXAM SET status = -1
                WHERE id = :examId AND site_id = :siteId
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("examId", examId)
                .addValue("siteId", siteId));
    }
}
