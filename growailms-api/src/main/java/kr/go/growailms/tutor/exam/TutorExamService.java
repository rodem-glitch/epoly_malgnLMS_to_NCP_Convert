package kr.go.growailms.tutor.exam;

import kr.go.growailms.tutor.common.TutorApiResponse;
import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 왜: exam_*.jsp 12개의 비즈니스 로직을 모아둔 서비스입니다.
 *     각 JSP에서 init.jsp 인증 이후 수행하던 로직(권한 체크, 데이터 가공, DB 호출)을 담당합니다.
 */
@Service
public class TutorExamService {

    private static final Logger log = LoggerFactory.getLogger(TutorExamService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TutorExamJdbcRepository repo;

    public TutorExamService(TutorExamJdbcRepository repo) {
        this.repo = repo;
    }

    // ========== 권한 확인 공통 메서드 ==========

    private boolean canAccessCourse(TutorAuthContext.AuthInfo auth, int courseId) {
        // 왜: 관리자는 전체 과목, 교수자는 주담당 과목만 접근 가능합니다.
        return auth.isAdmin() || repo.isMajorTutor(auth.userId(), courseId, auth.siteId());
    }

    // ========== exam_list.jsp ==========

    public List<Map<String, Object>> listExams(TutorAuthContext.AuthInfo auth, int courseId) {
        if (!canAccessCourse(auth, courseId)) {
            return null; // 왜: null은 권한 없음, 빈 리스트와 구분합니다.
        }

        if (!repo.courseExists(courseId, auth.siteId())) {
            return null;
        }

        List<Map<String, Object>> rows = repo.listExamsByCourse(courseId, auth.siteId());

        // 왜: JSP에서 while(list.next()) 루프 안에서 하던 날짜 포맷 변환을 재현합니다.
        for (Map<String, Object> row : rows) {
            String startDate = str(row, "start_date");
            String endDate = str(row, "end_date");
            row.put("start_date_conv", !startDate.isEmpty() ? formatDateTime(startDate) : "");
            row.put("end_date_conv", !endDate.isEmpty() ? formatDateTime(endDate) : "");
        }
        return rows;
    }

    // ========== exam_insert.jsp ==========

    public Map<String, Object> insertExam(TutorAuthContext.AuthInfo auth, int courseId,
                                          String title, String examDate, String examTime,
                                          int duration, String description, int questionCount,
                                          int totalScore, String allowRetake, String showResults,
                                          String onoffType) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!canAccessCourse(auth, courseId)) {
            result.put("code", TutorApiResponse.CODE_NO_EDIT_PERMISSION);
            result.put("message", "해당 과목에 시험을 등록할 권한이 없습니다.");
            return result;
        }

        if (!repo.courseExists(courseId, auth.siteId())) {
            result.put("code", TutorApiResponse.CODE_NOT_FOUND);
            result.put("message", "해당 과목이 없습니다.");
            return result;
        }

        // 왜: 필수값 검증
        if (title == null || title.trim().isEmpty()) {
            result.put("code", TutorApiResponse.CODE_MISSING_REQUIRED);
            result.put("message", "필수값이 누락되었습니다.");
            return result;
        }

        String retakeYn = ("true".equalsIgnoreCase(allowRetake) || "Y".equalsIgnoreCase(allowRetake)) ? "Y" : "N";
        String resultYn = ("false".equalsIgnoreCase(showResults) || "N".equalsIgnoreCase(showResults)) ? "N" : "Y";
        if (onoffType == null || onoffType.isEmpty()) onoffType = "F";
        int assignScore = Math.max(0, totalScore);
        int questionCnt = Math.max(0, questionCount);
        int examTimeMin = Math.max(0, duration);

        // 왜: 날짜/시간 포맷이 깨지면 LM_COURSE_MODULE 시작/종료일 저장이 꼬여서, 조회/정렬이 틀어질 수 있습니다.
        String startDateTime = buildStartDateTime(examDate, examTime);
        String endDateTime = buildEndDateTime(startDateTime, examTimeMin);

        // 왜: LM_EXAM 생성
        long newId;
        try {
            newId = repo.insertExam(auth.siteId(), title.trim(), examTimeMin, description,
                    questionCnt, retakeYn, onoffType, auth.userId());
        } catch (Exception e) {
            log.error("시험 저장 실패: courseId={}, title={}", courseId, title, e);
            result.put("code", TutorApiResponse.CODE_DB_FAIL);
            result.put("message", "시험 저장 중 오류가 발생했습니다.");
            return result;
        }

        // 왜: LM_COURSE_MODULE 생성 (과목 배치)
        try {
            int updated = repo.insertCourseModule(courseId, auth.siteId(), newId, title.trim(),
                    assignScore, startDateTime, endDateTime, retakeYn, resultYn);
            if (updated <= 0) {
                // 왜: 시험은 생성됐는데 과목 배치가 실패하면 "유령 시험"이 생기므로 soft-delete합니다.
                repo.softDeleteExam(newId, auth.siteId());
                result.put("code", "2001");
                result.put("message", "과목 배치 저장 중 오류가 발생했습니다.");
                return result;
            }
        } catch (Exception e) {
            log.error("과목 배치 저장 실패: courseId={}, examId={}", courseId, newId, e);
            repo.softDeleteExam(newId, auth.siteId());
            result.put("code", "2001");
            result.put("message", "과목 배치 저장 중 오류가 발생했습니다.");
            return result;
        }

        log.info("시험 생성: examId={}, courseId={}, title={}", newId, courseId, title);
        result.put("code", TutorApiResponse.CODE_SUCCESS);
        result.put("data", newId);
        return result;
    }

    // ========== exam_modify.jsp ==========

    public Map<String, Object> modifyExam(TutorAuthContext.AuthInfo auth, int courseId, int examId,
                                          String title, String examDate, String examTime,
                                          int duration, String description, int questionCount,
                                          int totalScore, String allowRetake, String showResults,
                                          String onoffType) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!canAccessCourse(auth, courseId)) {
            result.put("code", TutorApiResponse.CODE_NO_EDIT_PERMISSION);
            result.put("message", "해당 과목의 시험 정보를 수정할 권한이 없습니다.");
            return result;
        }

        if (!repo.courseExists(courseId, auth.siteId())) {
            result.put("code", TutorApiResponse.CODE_NOT_FOUND);
            result.put("message", "해당 과목이 없습니다.");
            return result;
        }

        Optional<Map<String, Object>> moduleOpt = repo.findCourseModule(courseId, examId);
        if (moduleOpt.isEmpty()) {
            result.put("code", TutorApiResponse.CODE_USER_NOT_FOUND);
            result.put("message", "해당 시험이 과목에 배치되어 있지 않습니다.");
            return result;
        }

        Optional<Map<String, Object>> examOpt = repo.findExam(examId, auth.siteId());
        if (examOpt.isEmpty()) {
            result.put("code", "4042");
            result.put("message", "시험 정보가 없습니다.");
            return result;
        }

        // 왜: 필수값 검증
        if (title == null || title.trim().isEmpty()) {
            result.put("code", TutorApiResponse.CODE_MISSING_REQUIRED);
            result.put("message", "필수값이 누락되었습니다.");
            return result;
        }

        String retakeYn = ("true".equalsIgnoreCase(allowRetake) || "Y".equalsIgnoreCase(allowRetake)) ? "Y" : "N";
        String resultYn = ("false".equalsIgnoreCase(showResults) || "N".equalsIgnoreCase(showResults)) ? "N" : "Y";
        if (onoffType == null || onoffType.isEmpty()) {
            onoffType = str(examOpt.get(), "onoff_type");
        }
        int assignScore = Math.max(0, totalScore);
        int questionCnt = Math.max(0, questionCount);
        int examTimeMin = Math.max(0, duration);

        String startDateTime = buildStartDateTime(examDate, examTime);
        String endDateTime = buildEndDateTime(startDateTime, examTimeMin);

        // 왜: LM_EXAM 수정
        int examUpdated = repo.updateExam(examId, auth.siteId(), title.trim(), examTimeMin,
                description, questionCnt, retakeYn, onoffType);
        if (examUpdated <= 0) {
            result.put("code", TutorApiResponse.CODE_DB_FAIL);
            result.put("message", "시험 수정 중 오류가 발생했습니다.");
            return result;
        }

        // 왜: LM_COURSE_MODULE 수정
        int moduleUpdated = repo.updateCourseModule(courseId, examId, title.trim(),
                assignScore, startDateTime, endDateTime, retakeYn, resultYn);
        if (moduleUpdated <= 0) {
            result.put("code", "2001");
            result.put("message", "과목 배치 수정 중 오류가 발생했습니다.");
            return result;
        }

        log.info("시험 수정: examId={}, courseId={}", examId, courseId);
        result.put("code", TutorApiResponse.CODE_SUCCESS);
        result.put("data", examId);
        return result;
    }

    // ========== exam_delete.jsp ==========

    public Map<String, Object> deleteExam(TutorAuthContext.AuthInfo auth, int courseId, int examId) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!canAccessCourse(auth, courseId)) {
            result.put("code", TutorApiResponse.CODE_NO_EDIT_PERMISSION);
            result.put("message", "해당 과목의 시험을 삭제할 권한이 없습니다.");
            return result;
        }

        if (!repo.courseExists(courseId, auth.siteId())) {
            result.put("code", TutorApiResponse.CODE_NOT_FOUND);
            result.put("message", "해당 과목이 없습니다.");
            return result;
        }

        Optional<Map<String, Object>> moduleOpt = repo.findCourseModule(courseId, examId);
        if (moduleOpt.isEmpty()) {
            result.put("code", TutorApiResponse.CODE_USER_NOT_FOUND);
            result.put("message", "해당 시험이 과목에 배치되어 있지 않습니다.");
            return result;
        }

        // 왜: 응시/채점 내역이 있으면 배치만 지워도 데이터가 끊기므로 안전하게 막습니다.
        if (repo.hasSubmissions(examId, courseId)) {
            result.put("code", "4090");
            result.put("message", "응시/채점 내역이 있어 삭제할 수 없습니다.");
            return result;
        }

        // 왜: 배치 삭제
        int deleted = repo.deleteCourseModule(courseId, examId);
        if (deleted <= 0) {
            result.put("code", TutorApiResponse.CODE_DB_FAIL);
            result.put("message", "삭제 중 오류가 발생했습니다.");
            return result;
        }

        // 왜: 다른 과목에서 쓰지 않는 시험이면 soft-delete로 정리합니다.
        try {
            if (!repo.isExamUsedElsewhere(examId)) {
                repo.softDeleteExam(examId, auth.siteId());
            }
        } catch (Exception e) {
            log.warn("시험 soft-delete 중 무시된 오류: examId={}", examId, e);
        }

        log.info("시험 삭제: examId={}, courseId={}", examId, courseId);
        result.put("code", TutorApiResponse.CODE_SUCCESS);
        result.put("data", examId);
        return result;
    }

    // ========== exam_link.jsp ==========

    public Map<String, Object> linkExam(TutorAuthContext.AuthInfo auth, int courseId, int examId,
                                        String startDate, String endDate,
                                        int assignScore, String retryYn, String resultYn) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!canAccessCourse(auth, courseId)) {
            result.put("code", TutorApiResponse.CODE_NO_EDIT_PERMISSION);
            result.put("message", "해당 과목에 시험을 등록할 권한이 없습니다.");
            return result;
        }

        if (!repo.courseExists(courseId, auth.siteId())) {
            result.put("code", TutorApiResponse.CODE_NOT_FOUND);
            result.put("message", "해당 과목이 없습니다.");
            return result;
        }

        Optional<Map<String, Object>> examOpt = repo.findExam(examId, auth.siteId());
        if (examOpt.isEmpty()) {
            result.put("code", TutorApiResponse.CODE_USER_NOT_FOUND);
            result.put("message", "시험 정보가 없습니다.");
            return result;
        }

        // 왜: 이미 연결된 시험인지 확인하여 중복 연결을 방지합니다.
        if (repo.isExamLinked(courseId, examId)) {
            result.put("code", "4042");
            result.put("message", "이미 등록된 시험입니다.");
            return result;
        }

        retryYn = "Y".equals(retryYn) ? "Y" : "N";
        resultYn = "N".equals(resultYn) ? "N" : "Y";

        // 왜: 기본값 처리 - 시작일이 없으면 오늘 09시, 종료일이 없으면 시작일+7일 18시
        String today = LocalDate.now().format(DATE_FORMAT);
        if (startDate == null || startDate.isEmpty()) {
            startDate = today + "090000";
        }
        if (endDate == null || endDate.isEmpty()) {
            try {
                String baseDateStr = startDate.length() >= 8 ? startDate.substring(0, 8) : today;
                LocalDate baseDate = LocalDate.parse(baseDateStr, DATE_FORMAT);
                endDate = baseDate.plusDays(7).format(DATE_FORMAT) + "180000";
            } catch (Exception e) {
                endDate = today + "180000";
            }
        }

        String examNm = str(examOpt.get(), "exam_nm");

        int updated = repo.insertCourseModule(courseId, auth.siteId(), examId, examNm,
                assignScore, startDate, endDate, retryYn, resultYn);
        if (updated <= 0) {
            result.put("code", "2001");
            result.put("message", "과목에 시험 연결 중 오류가 발생했습니다.");
            return result;
        }

        log.info("시험 연결: examId={}, courseId={}", examId, courseId);
        result.put("code", TutorApiResponse.CODE_SUCCESS);
        result.put("data", examId);
        return result;
    }

    // ========== exam_score_update.jsp ==========

    public Map<String, Object> updateScore(TutorAuthContext.AuthInfo auth, int courseId, int examId,
                                           int courseUserId, double markingScore) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!canAccessCourse(auth, courseId)) {
            result.put("code", TutorApiResponse.CODE_NO_EDIT_PERMISSION);
            result.put("message", "해당 과목의 시험 점수를 수정할 권한이 없습니다.");
            return result;
        }

        // 왜: marking_score는 0~100 사이의 퍼센트 값이어야 합니다.
        if (markingScore < 0 || markingScore > 100) {
            result.put("code", TutorApiResponse.CODE_BUSINESS_ERROR);
            result.put("message", "marking_score는 0~100 사이여야 합니다.");
            return result;
        }

        // 왜: 시험 배치 정보에서 assign_score(배점)를 가져와 실제 점수로 변환합니다.
        Optional<Map<String, Object>> moduleOpt = repo.findCourseModuleWithExam(courseId, examId, auth.siteId());
        if (moduleOpt.isEmpty()) {
            result.put("code", TutorApiResponse.CODE_NOT_FOUND);
            result.put("message", "해당 시험이 과목에 배치되어 있지 않습니다.");
            return result;
        }

        Optional<Map<String, Object>> cuOpt = repo.findCourseUser(courseUserId, courseId, auth.siteId());
        if (cuOpt.isEmpty()) {
            result.put("code", TutorApiResponse.CODE_USER_NOT_FOUND);
            result.put("message", "해당 수강 정보가 없습니다.");
            return result;
        }

        double assignScoreVal = toDouble(moduleOpt.get(), "assign_score");
        // 왜: marking_score(%)를 assign_score(배점) 기준으로 실제 점수로 변환합니다.
        double score = Math.min(assignScoreVal, assignScoreVal * markingScore / 100.0);
        String now = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());

        Optional<Map<String, Object>> euOpt = repo.findExamUser(examId, courseUserId);
        if (euOpt.isPresent()) {
            // 왜: 기존 응시 레코드가 있으면 점수만 업데이트합니다.
            int updated = repo.updateExamUserScore(examId, courseUserId, markingScore,
                    score, auth.userId(), now);
            if (updated <= 0) {
                result.put("code", TutorApiResponse.CODE_DB_FAIL);
                result.put("message", "점수 저장 중 오류가 발생했습니다.");
                return result;
            }
        } else {
            // 왜: 오프라인 시험은 학생이 온라인으로 응시하지 않을 수 있으므로,
            //     채점 시점에 응시 레코드를 자동으로 만들어 줍니다.
            long studentUserId = ((Number) cuOpt.get().get("user_id")).longValue();
            int inserted = repo.insertExamUser(examId, courseUserId, courseId,
                    studentUserId, auth.siteId(), markingScore, score,
                    auth.userId(), now, "0.0.0.0");
            if (inserted <= 0) {
                result.put("code", TutorApiResponse.CODE_DB_FAIL);
                result.put("message", "점수 저장 중 오류가 발생했습니다.");
                return result;
            }
        }

        // 왜: 성적 반영 - 과목 시험 점수를 재계산합니다.
        repo.recalcCourseExamScore(courseUserId);

        log.info("시험 점수 저장: examId={}, courseUserId={}, markingScore={}", examId, courseUserId, markingScore);
        result.put("code", TutorApiResponse.CODE_SUCCESS);
        result.put("data", courseUserId);
        return result;
    }

    // ========== exam_submit_cancel.jsp ==========

    public Map<String, Object> cancelSubmit(TutorAuthContext.AuthInfo auth, int courseId,
                                            int examId, int courseUserId) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!canAccessCourse(auth, courseId)) {
            result.put("code", TutorApiResponse.CODE_NO_EDIT_PERMISSION);
            result.put("message", "해당 과목의 시험 응시를 취소할 권한이 없습니다.");
            return result;
        }

        Optional<Map<String, Object>> cuOpt = repo.findCourseUser(courseUserId, courseId, auth.siteId());
        if (cuOpt.isEmpty()) {
            result.put("code", TutorApiResponse.CODE_USER_NOT_FOUND);
            result.put("message", "해당 수강 정보가 없습니다.");
            return result;
        }

        // 왜: 응시 기록이 없으면 취소할 대상이 없으므로 안내만 합니다.
        Optional<Map<String, Object>> euOpt = repo.findExamUser(examId, courseUserId);
        if (euOpt.isEmpty()) {
            result.put("code", "4042");
            result.put("message", "해당 응시 정보가 없습니다.");
            return result;
        }

        // 왜: 결과 삭제 후 응시 삭제 순서를 지켜야 참조 무결성이 유지됩니다.
        repo.deleteExamResults(examId, courseUserId);
        int deleted = repo.deleteExamUser(examId, courseUserId);
        if (deleted <= 0) {
            result.put("code", TutorApiResponse.CODE_DB_FAIL);
            result.put("message", "응시 취소 중 오류가 발생했습니다.");
            return result;
        }

        // 왜: 성적 반영 - 응시 삭제 후 과목 시험 점수를 재계산합니다.
        repo.recalcCourseExamScore(courseUserId);

        log.info("시험 응시 취소: examId={}, courseUserId={}", examId, courseUserId);
        result.put("code", TutorApiResponse.CODE_SUCCESS);
        result.put("data", courseUserId);
        return result;
    }

    // ========== exam_users.jsp ==========

    public Map<String, Object> listExamUsers(TutorAuthContext.AuthInfo auth, int courseId, int examId) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!canAccessCourse(auth, courseId)) {
            result.put("code", TutorApiResponse.CODE_NO_EDIT_PERMISSION);
            result.put("message", "해당 과목의 시험 제출현황을 조회할 권한이 없습니다.");
            return result;
        }

        Optional<Map<String, Object>> moduleOpt = repo.findCourseModuleDetail(courseId, examId, auth.siteId());
        if (moduleOpt.isEmpty()) {
            result.put("code", TutorApiResponse.CODE_NOT_FOUND);
            result.put("message", "해당 시험이 과목에 배치되어 있지 않습니다.");
            return result;
        }

        Map<String, Object> minfo = moduleOpt.get();
        double assignScoreVal = toDouble(minfo, "assign_score");

        List<Map<String, Object>> users = repo.listExamUsers(courseId, examId, auth.siteId());

        // 왜: JSP에서 while(list.next()) 루프 안에서 하던 화면용 가공을 재현합니다.
        for (Map<String, Object> row : users) {
            boolean submitted = "Y".equals(str(row, "submit_yn"));
            row.put("submitted", submitted);
            row.put("submitted_at", submitted && !str(row, "submit_date").isEmpty()
                    ? formatDateTime(str(row, "submit_date")) : "-");
            row.put("confirm", "Y".equals(str(row, "confirm_yn")));
            row.put("confirm_at", !str(row, "confirm_date").isEmpty()
                    ? formatDateTime(str(row, "confirm_date")) : "-");

            double marking = toDouble(row, "marking_score");
            double convScore = Math.min(assignScoreVal, assignScoreVal * marking / 100.0);
            row.put("marking_score_conv", String.format("%.0f", marking));
            row.put("score_conv", String.format("%.2f", convScore));
        }

        result.put("code", TutorApiResponse.CODE_SUCCESS);
        result.put("data", users);
        result.put("exam", minfo);
        return result;
    }

    // ========== exam_template_list.jsp ==========

    public Map<String, Object> listExamTemplates(TutorAuthContext.AuthInfo auth, int page, int limit) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (page <= 0) page = 1;
        if (limit <= 0) limit = 50;

        List<Map<String, Object>> rows = repo.listExamTemplates(
                auth.siteId(), auth.isAdmin(), auth.userId(), page, limit);
        int total = repo.countExamTemplates(auth.siteId(), auth.isAdmin(), auth.userId());

        // 왜: JSP에서 하던 포맷팅과 총점 계산을 재현합니다.
        for (Map<String, Object> row : rows) {
            row.put("reg_date_conv", formatDate8(str(row, "reg_date")));
            row.put("onoff_type_conv", convertOnoffType(str(row, "onoff_type")));

            // 왜: 난이도별 배점 * 문항수로 총점을 계산합니다.
            int totalPoints = 0;
            for (int i = 1; i <= 6; i++) {
                int mcnt = toInt(row, "mcnt" + i);
                int tcnt = toInt(row, "tcnt" + i);
                int assign = toInt(row, "assign" + i);
                totalPoints += (mcnt + tcnt) * assign;
            }
            row.put("total_points", totalPoints);
        }

        result.put("code", TutorApiResponse.CODE_SUCCESS);
        result.put("data", rows);
        result.put("total", total);
        result.put("page", page);
        result.put("limit", limit);
        return result;
    }

    // ========== exam_template_insert.jsp ==========

    public Map<String, Object> insertExamTemplate(TutorAuthContext.AuthInfo auth,
                                                   String examName, int examTime,
                                                   String shuffleYn, int passingScore,
                                                   String questionIds, String content) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (examName == null || examName.trim().isEmpty()) {
            result.put("code", TutorApiResponse.CODE_MISSING_PARAM);
            result.put("message", "시험명이 필요합니다.");
            return result;
        }

        if (examTime <= 0) examTime = 60;
        shuffleYn = "Y".equals(shuffleYn) ? "Y" : "N";

        // 왜: 문제 ID 목록을 파싱하여 유효한 숫자만 추출합니다.
        List<Integer> questionIdList = parseQuestionIds(questionIds);

        // 왜: 시험 저장 시 난이도별 문항수/배점이 필수라서 문제 목록으로 직접 계산합니다.
        int[] mcnt = new int[7];
        int[] tcnt = new int[7];
        int questionCntFinal = 0;

        if (!questionIdList.isEmpty()) {
            List<Map<String, Object>> questions = repo.findQuestionsByIds(questionIdList, auth.siteId());
            for (Map<String, Object> q : questions) {
                questionCntFinal++;
                int grade = toInt(q, "grade");
                if (grade < 1 || grade > 6) grade = 1;

                String qtype = str(q, "question_type");
                if ("1".equals(qtype) || "2".equals(qtype)) {
                    mcnt[grade]++;
                } else {
                    tcnt[grade]++;
                }
            }
        }

        int[] assigns = new int[7];
        for (int i = 1; i <= 6; i++) {
            assigns[i] = (mcnt[i] + tcnt[i]) > 0 ? 1 : 0;
        }
        if (passingScore > 0) assigns[1] = passingScore;

        String rangeIdx = buildRangeIdx(questionIdList);

        try {
            long newId = repo.insertExamTemplate(auth.siteId(), examName.trim(), examTime,
                    shuffleYn, content != null ? content : "", rangeIdx,
                    questionCntFinal, mcnt, tcnt, assigns, auth.userId());

            log.info("시험 템플릿 등록: id={}, examName={}", newId, examName);
            result.put("code", TutorApiResponse.CODE_SUCCESS);
            result.put("message", "시험이 등록되었습니다.");
            result.put("data", newId);
        } catch (Exception e) {
            log.error("시험 템플릿 등록 실패: examName={}", examName, e);
            result.put("code", "5000");
            result.put("message", "시험 등록 중 오류가 발생했습니다.");
        }
        return result;
    }

    // ========== exam_template_modify.jsp ==========

    public Map<String, Object> modifyExamTemplate(TutorAuthContext.AuthInfo auth, int examId,
                                                   String examName, int examTime,
                                                   String shuffleYn, int passingScore,
                                                   String questionIds, String content) {
        Map<String, Object> result = new LinkedHashMap<>();

        Optional<Map<String, Object>> examOpt = repo.findExam(examId, auth.siteId());
        if (examOpt.isEmpty()) {
            result.put("code", TutorApiResponse.CODE_NOT_FOUND);
            result.put("message", "해당 시험이 존재하지 않습니다.");
            return result;
        }

        // 왜: 교수자는 본인이 등록한 시험만, 관리자는 전체 수정 가능합니다.
        Map<String, Object> examInfo = examOpt.get();
        int managerId = toInt(examInfo, "manager_id");
        if (!auth.isAdmin() && managerId != (int) auth.userId() && managerId != -99) {
            result.put("code", TutorApiResponse.CODE_NO_PERMISSION);
            result.put("message", "해당 시험을 수정할 권한이 없습니다.");
            return result;
        }

        // 왜: 문제 목록이 바뀌면 난이도별 문항수/배점도 같이 맞춰야 합니다.
        List<Integer> questionIdList = parseQuestionIds(questionIds);

        int[] mcnt = new int[7];
        int[] tcnt = new int[7];
        int questionCntFinal = 0;

        if (!questionIdList.isEmpty()) {
            List<Map<String, Object>> questions = repo.findQuestionsByIds(questionIdList, auth.siteId());
            for (Map<String, Object> q : questions) {
                questionCntFinal++;
                int grade = toInt(q, "grade");
                if (grade < 1 || grade > 6) grade = 1;

                String qtype = str(q, "question_type");
                if ("1".equals(qtype) || "2".equals(qtype)) {
                    mcnt[grade]++;
                } else {
                    tcnt[grade]++;
                }
            }
        }

        int[] assigns = new int[7];
        for (int i = 1; i <= 6; i++) {
            assigns[i] = (mcnt[i] + tcnt[i]) > 0 ? 1 : 0;
        }
        if (passingScore > 0) assigns[1] = passingScore;

        String rangeIdx = buildRangeIdx(questionIdList);

        try {
            int updated = repo.updateExamTemplate(examId, auth.siteId(), examName, examTime,
                    shuffleYn, content, rangeIdx, questionCntFinal, mcnt, tcnt, assigns);
            if (updated <= 0) {
                result.put("code", "5000");
                result.put("message", "시험 수정 중 오류가 발생했습니다.");
                return result;
            }

            log.info("시험 템플릿 수정: id={}", examId);
            result.put("code", TutorApiResponse.CODE_SUCCESS);
            result.put("message", "시험이 수정되었습니다.");
            result.put("data", examId);
        } catch (Exception e) {
            log.error("시험 템플릿 수정 실패: examId={}", examId, e);
            result.put("code", "5000");
            result.put("message", "시험 수정 중 오류가 발생했습니다.");
        }
        return result;
    }

    // ========== exam_template_delete.jsp ==========

    public Map<String, Object> deleteExamTemplate(TutorAuthContext.AuthInfo auth, int examId) {
        Map<String, Object> result = new LinkedHashMap<>();

        Optional<Map<String, Object>> examOpt = repo.findExam(examId, auth.siteId());
        if (examOpt.isEmpty()) {
            result.put("code", TutorApiResponse.CODE_NOT_FOUND);
            result.put("message", "해당 시험이 존재하지 않습니다.");
            return result;
        }

        // 왜: 교수자는 본인이 등록한 시험만, 관리자는 전체 삭제 가능합니다.
        Map<String, Object> examInfo = examOpt.get();
        int managerId = toInt(examInfo, "manager_id");
        if (!auth.isAdmin() && managerId != (int) auth.userId() && managerId != -99) {
            result.put("code", TutorApiResponse.CODE_NO_PERMISSION);
            result.put("message", "해당 시험을 삭제할 권한이 없습니다.");
            return result;
        }

        int updated = repo.softDeleteExamTemplate(examId, auth.siteId());
        if (updated <= 0) {
            result.put("code", "5000");
            result.put("message", "시험 삭제 중 오류가 발생했습니다.");
            return result;
        }

        log.info("시험 템플릿 삭제: id={}", examId);
        result.put("code", TutorApiResponse.CODE_SUCCESS);
        result.put("message", "시험이 삭제되었습니다.");
        result.put("data", examId);
        return result;
    }

    // ==================== 내부 유틸리티 ====================

    private String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v != null ? v.toString().trim() : "";
    }

    private int toInt(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double toDouble(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * 왜: yyyyMMddHHmmss 형식의 날짜를 yyyy.MM.dd HH:mm 형식으로 변환합니다.
     */
    private String formatDateTime(String yyyymmddhhmmss) {
        if (yyyymmddhhmmss == null || yyyymmddhhmmss.length() < 12) return yyyymmddhhmmss;
        return yyyymmddhhmmss.substring(0, 4) + "." + yyyymmddhhmmss.substring(4, 6) + "."
                + yyyymmddhhmmss.substring(6, 8) + " " + yyyymmddhhmmss.substring(8, 10)
                + ":" + yyyymmddhhmmss.substring(10, 12);
    }

    /**
     * 왜: yyyyMMdd... 형식의 날짜를 yyyy.MM.dd 형식으로 변환합니다.
     */
    private String formatDate8(String dateStr) {
        if (dateStr == null || dateStr.length() < 8) return "";
        return dateStr.substring(0, 4) + "." + dateStr.substring(4, 6) + "." + dateStr.substring(6, 8);
    }

    private String convertOnoffType(String onoffType) {
        return switch (onoffType) {
            case "O" -> "온라인";
            case "N" -> "온라인";
            case "F" -> "오프라인";
            case "B" -> "혼합";
            default -> onoffType;
        };
    }

    /**
     * 왜: 시험 날짜/시간을 yyyyMMddHHmmss 형식으로 조합합니다.
     *     날짜/시간 포맷이 깨지면 LM_COURSE_MODULE 시작/종료일 저장이 꼬여서 조회/정렬이 틀어질 수 있습니다.
     */
    private String buildStartDateTime(String examDate, String examTime) {
        String startYmd;
        try {
            // 왜: examDate가 yyyy-MM-dd 형식이면 yyyyMMdd로 변환합니다.
            startYmd = examDate.replace("-", "").replace(".", "").replace("/", "");
            if (startYmd.length() > 8) startYmd = startYmd.substring(0, 8);
        } catch (Exception e) {
            startYmd = LocalDate.now().format(DATE_FORMAT);
        }

        String startH = "00";
        String startM = "00";
        if (examTime != null && examTime.length() >= 5) {
            startH = examTime.substring(0, 2);
            startM = examTime.substring(3, 5);
        }
        return startYmd + startH + startM + "00";
    }

    /**
     * 왜: 종료일시를 시작일시 + duration(분)으로 계산합니다.
     */
    private String buildEndDateTime(String startDateTime, int durationMinutes) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            Date d = sdf.parse(startDateTime);
            Calendar cal = Calendar.getInstance();
            cal.setTime(d);
            cal.add(Calendar.MINUTE, Math.max(0, durationMinutes));
            return sdf.format(cal.getTime());
        } catch (Exception e) {
            return startDateTime;
        }
    }

    /**
     * 왜: 쉼표 구분 문제 ID 문자열을 정수 리스트로 변환합니다.
     */
    private List<Integer> parseQuestionIds(String questionIds) {
        List<Integer> result = new ArrayList<>();
        if (questionIds == null || questionIds.trim().isEmpty()) {
            return result;
        }
        String[] rawIds = questionIds.split(",");
        for (String rawId : rawIds) {
            String qid = rawId.trim();
            if (qid.matches("\\d+")) {
                result.add(Integer.parseInt(qid));
            }
        }
        return result;
    }

    /**
     * 왜: 문제 ID 리스트를 쉼표 구분 문자열로 변환합니다 (range_idx 저장용).
     */
    private String buildRangeIdx(List<Integer> questionIdList) {
        if (questionIdList.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < questionIdList.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(questionIdList.get(i));
        }
        return sb.toString();
    }
}
