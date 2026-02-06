package kr.go.growailms.tutor.homework;

import kr.go.growailms.tutor.common.TutorApiResponse;
import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 왜: homework_*.jsp 11개의 비즈니스 로직을 모아둔 서비스입니다.
 *     각 JSP에서 init.jsp 인증 이후 수행하던 로직(권한 체크, 데이터 가공, DB 호출)을 담당합니다.
 */
@Service
public class TutorHomeworkService {

    private static final Logger log = LoggerFactory.getLogger(TutorHomeworkService.class);
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TutorHomeworkJdbcRepository repo;

    public TutorHomeworkService(TutorHomeworkJdbcRepository repo) {
        this.repo = repo;
    }

    // ========== 권한 확인 공통 메서드 ==========

    private boolean canAccessCourse(TutorAuthContext.AuthInfo auth, int courseId) {
        // 왜: 관리자는 전체 과목, 교수자는 주담당 과목만 접근 가능합니다.
        return auth.isAdmin() || repo.isMajorTutor(auth.userId(), courseId, auth.siteId());
    }

    // ========== 콘텐츠 검증 공통 메서드 ==========

    /**
     * 왜: base64 이미지는 DB에 누적되면 용량 폭증/오류가 나기 쉽습니다.
     *     JSP에서 하던 동일한 검증을 재현합니다.
     */
    private boolean containsBase64Image(String content) {
        return content != null
                && content.contains("<img")
                && content.contains("data:image/")
                && content.contains("base64");
    }

    /**
     * 왜: 내용이 60000바이트를 초과하면 DB TEXT 컬럼 한계에 걸릴 수 있습니다.
     */
    private int getByteLength(String content) {
        try {
            return content.replace("\r\n", "\n").getBytes("UTF-8").length;
        } catch (UnsupportedEncodingException e) {
            // 왜: UTF-8은 Java에서 항상 지원되므로 발생하지 않지만, 안전장치를 둡니다.
            return content.length();
        }
    }

    // ========== homework_list.jsp ==========

    public Map<String, Object> listHomeworks(TutorAuthContext.AuthInfo auth, int courseId) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (courseId == 0) {
            result.put("rst_code", TutorApiResponse.CODE_MISSING_PARAM);
            result.put("rst_message", "course_id가 필요합니다.");
            return result;
        }

        // 왜: 교수자는 주담당 과목만, 관리자는 전체 과목에 접근 가능합니다.
        if (!canAccessCourse(auth, courseId)) {
            result.put("rst_code", TutorApiResponse.CODE_NO_EDIT_PERMISSION);
            result.put("rst_message", "해당 과목의 과제 정보를 조회할 권한이 없습니다.");
            return result;
        }

        if (!repo.courseExists(courseId, auth.siteId())) {
            result.put("rst_code", TutorApiResponse.CODE_NOT_FOUND);
            result.put("rst_message", "해당 과목이 없습니다.");
            return result;
        }

        List<Map<String, Object>> list = repo.listHomeworks(courseId, auth.siteId());

        // 왜: JSP에서 while(list.next()) 루프 안에서 하던 날짜 포맷 가공을 재현합니다.
        for (Map<String, Object> row : list) {
            row.put("start_date_conv", formatDateTime(str(row, "start_date")));
            row.put("end_date_conv", formatDateTime(str(row, "end_date")));
        }

        result.put("rst_code", TutorApiResponse.CODE_SUCCESS);
        result.put("rst_message", "성공");
        result.put("rst_data", list);
        return result;
    }

    // ========== homework_insert.jsp ==========

    public Map<String, Object> insertHomework(TutorAuthContext.AuthInfo auth, int courseId,
                                               String title, String description,
                                               String dueDate, String dueTime,
                                               int totalScore, String onoffType) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (courseId == 0) {
            result.put("rst_code", TutorApiResponse.CODE_MISSING_PARAM);
            result.put("rst_message", "course_id가 필요합니다.");
            return result;
        }
        if (title == null || title.trim().isEmpty()) {
            result.put("rst_code", TutorApiResponse.CODE_MISSING_REQUIRED);
            result.put("rst_message", "필수값이 누락되었습니다.");
            return result;
        }

        // 왜: 교수자는 주담당 과목만 수정 가능합니다.
        if (!canAccessCourse(auth, courseId)) {
            result.put("rst_code", TutorApiResponse.CODE_NO_EDIT_PERMISSION);
            result.put("rst_message", "해당 과목에 과제를 등록할 권한이 없습니다.");
            return result;
        }

        if (!repo.courseExists(courseId, auth.siteId())) {
            result.put("rst_code", TutorApiResponse.CODE_NOT_FOUND);
            result.put("rst_message", "해당 과목이 없습니다.");
            return result;
        }

        String content = description != null ? description : "";

        // 왜: base64 이미지는 DB에 누적되면 용량 폭증/오류가 나기 쉽습니다.
        if (containsBase64Image(content)) {
            result.put("rst_code", "1101");
            result.put("rst_message", "이미지는 첨부파일로 업로드해 주세요.");
            return result;
        }
        int bytes = getByteLength(content);
        if (bytes > 60000) {
            result.put("rst_code", "1102");
            result.put("rst_message", "내용은 60000바이트를 초과할 수 없습니다. (현재 " + bytes + "바이트)");
            return result;
        }

        title = title.trim();
        int assignScore = Math.max(0, totalScore);
        if (onoffType == null || onoffType.isEmpty()) {
            onoffType = "N";
        }

        // 왜: 마감일시를 yyyyMMddHHmmss 형식으로 변환합니다 (JSP의 m.time("yyyyMMdd", dueDate) 재현).
        String endDateTime = buildEndDateTime(dueDate, dueTime);
        // 왜: 시작일은 "지금"으로 두는 게 가장 안전합니다.
        String startDateTime = LocalDateTime.now().format(DATETIME_FORMAT);

        // 왜: LM_HOMEWORK 생성 후 LM_COURSE_MODULE에 배치를 생성합니다.
        long newId = repo.insertHomework(auth.siteId(), auth.userId(), title, content, onoffType);
        if (newId <= 0) {
            result.put("rst_code", TutorApiResponse.CODE_DB_FAIL);
            result.put("rst_message", "과제 저장 중 오류가 발생했습니다.");
            return result;
        }

        boolean moduleInserted = repo.insertCourseModule(
                courseId, auth.siteId(), newId, title, assignScore, startDateTime, endDateTime);
        if (!moduleInserted) {
            // 왜: 배치 실패 시 과제를 소프트 삭제하여 고아 데이터를 방지합니다.
            repo.softDeleteHomework(newId, auth.siteId());
            result.put("rst_code", "2001");
            result.put("rst_message", "과목 배치 저장 중 오류가 발생했습니다.");
            return result;
        }

        log.info("과제 생성: id={}, courseId={}, title={}", newId, courseId, title);

        result.put("rst_code", TutorApiResponse.CODE_SUCCESS);
        result.put("rst_message", "성공");
        result.put("rst_data", newId);
        return result;
    }

    // ========== homework_modify.jsp ==========

    public Map<String, Object> modifyHomework(TutorAuthContext.AuthInfo auth, int courseId,
                                               int homeworkId, String title, String description,
                                               String dueDate, String dueTime,
                                               int totalScore, String onoffType) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (courseId == 0 || homeworkId == 0) {
            result.put("rst_code", TutorApiResponse.CODE_MISSING_PARAM);
            result.put("rst_message", "course_id, homework_id가 필요합니다.");
            return result;
        }

        if (!canAccessCourse(auth, courseId)) {
            result.put("rst_code", TutorApiResponse.CODE_NO_EDIT_PERMISSION);
            result.put("rst_message", "해당 과목의 과제 정보를 수정할 권한이 없습니다.");
            return result;
        }

        if (!repo.courseExists(courseId, auth.siteId())) {
            result.put("rst_code", TutorApiResponse.CODE_NOT_FOUND);
            result.put("rst_message", "해당 과목이 없습니다.");
            return result;
        }

        if (!repo.courseModuleExists(courseId, homeworkId)) {
            result.put("rst_code", TutorApiResponse.CODE_USER_NOT_FOUND);
            result.put("rst_message", "해당 과제가 과목에 배치되어 있지 않습니다.");
            return result;
        }

        if (!repo.homeworkExists(homeworkId, auth.siteId())) {
            result.put("rst_code", "4042");
            result.put("rst_message", "과제 정보가 없습니다.");
            return result;
        }

        String content = description != null ? description : "";
        title = title != null ? title.trim() : "";

        // 왜: base64 이미지 검증
        if (containsBase64Image(content)) {
            result.put("rst_code", "1101");
            result.put("rst_message", "이미지는 첨부파일로 업로드해 주세요.");
            return result;
        }
        int bytes = getByteLength(content);
        if (bytes > 60000) {
            result.put("rst_code", "1102");
            result.put("rst_message", "내용은 60000바이트를 초과할 수 없습니다. (현재 " + bytes + "바이트)");
            return result;
        }

        int assignScore = Math.max(0, totalScore);
        if (onoffType == null || onoffType.isEmpty()) {
            onoffType = "N";
        }

        String endDateTime = buildEndDateTime(dueDate, dueTime);

        // 왜: LM_HOMEWORK와 LM_COURSE_MODULE을 함께 수정합니다.
        int hwUpdated = repo.updateHomework(homeworkId, auth.siteId(), title, content, onoffType);
        if (hwUpdated <= 0) {
            result.put("rst_code", TutorApiResponse.CODE_DB_FAIL);
            result.put("rst_message", "과제 수정 중 오류가 발생했습니다.");
            return result;
        }

        int modUpdated = repo.updateCourseModule(courseId, homeworkId, title, assignScore, endDateTime);
        if (modUpdated <= 0) {
            result.put("rst_code", "2001");
            result.put("rst_message", "과목 배치 수정 중 오류가 발생했습니다.");
            return result;
        }

        log.info("과제 수정: homeworkId={}, courseId={}", homeworkId, courseId);

        result.put("rst_code", TutorApiResponse.CODE_SUCCESS);
        result.put("rst_message", "성공");
        result.put("rst_data", homeworkId);
        return result;
    }

    // ========== homework_delete.jsp ==========

    public Map<String, Object> deleteHomework(TutorAuthContext.AuthInfo auth, int courseId, int homeworkId) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (courseId == 0 || homeworkId == 0) {
            result.put("rst_code", TutorApiResponse.CODE_MISSING_PARAM);
            result.put("rst_message", "course_id, homework_id가 필요합니다.");
            return result;
        }

        if (!canAccessCourse(auth, courseId)) {
            result.put("rst_code", TutorApiResponse.CODE_NO_EDIT_PERMISSION);
            result.put("rst_message", "해당 과목의 과제를 삭제할 권한이 없습니다.");
            return result;
        }

        if (!repo.courseExists(courseId, auth.siteId())) {
            result.put("rst_code", TutorApiResponse.CODE_NOT_FOUND);
            result.put("rst_message", "해당 과목이 없습니다.");
            return result;
        }

        if (!repo.courseModuleExists(courseId, homeworkId)) {
            result.put("rst_code", TutorApiResponse.CODE_USER_NOT_FOUND);
            result.put("rst_message", "해당 과제가 과목에 배치되어 있지 않습니다.");
            return result;
        }

        // 왜: 제출/채점 내역이 있으면 삭제할 수 없습니다.
        if (repo.hasHomeworkUserRecords(homeworkId, courseId)) {
            result.put("rst_code", "4090");
            result.put("rst_message", "제출/채점 내역이 있어 삭제할 수 없습니다.");
            return result;
        }

        boolean deleted = repo.deleteCourseModule(courseId, homeworkId);
        if (!deleted) {
            result.put("rst_code", TutorApiResponse.CODE_DB_FAIL);
            result.put("rst_message", "삭제 중 오류가 발생했습니다.");
            return result;
        }

        // 왜: 다른 과목에도 배치되어 있지 않으면 과제 자체를 소프트 삭제합니다.
        try {
            if (!repo.isHomeworkUsedInOtherCourses(homeworkId)) {
                repo.softDeleteHomework(homeworkId, auth.siteId());
            }
        } catch (Exception e) {
            log.warn("과제 소프트삭제 실패(무시): homeworkId={}, error={}", homeworkId, e.getMessage());
        }

        log.info("과제 삭제: homeworkId={}, courseId={}", homeworkId, courseId);

        result.put("rst_code", TutorApiResponse.CODE_SUCCESS);
        result.put("rst_message", "성공");
        result.put("rst_data", homeworkId);
        return result;
    }

    // ========== homework_feedback_update.jsp ==========

    public Map<String, Object> updateFeedback(TutorAuthContext.AuthInfo auth, int courseId,
                                               int homeworkId, int courseUserId,
                                               double markingScore, String feedback) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (courseId == 0 || homeworkId == 0 || courseUserId == 0) {
            result.put("rst_code", TutorApiResponse.CODE_MISSING_PARAM);
            result.put("rst_message", "course_id, homework_id, course_user_id가 필요합니다.");
            return result;
        }
        if (markingScore < 0 || markingScore > 100) {
            result.put("rst_code", TutorApiResponse.CODE_BUSINESS_ERROR);
            result.put("rst_message", "marking_score는 0~100 사이여야 합니다.");
            return result;
        }

        if (!canAccessCourse(auth, courseId)) {
            result.put("rst_code", TutorApiResponse.CODE_NO_EDIT_PERMISSION);
            result.put("rst_message", "해당 과목의 과제 피드백을 수정할 권한이 없습니다.");
            return result;
        }

        // 왜: 과목에 배치된 과제의 assign_score를 조회합니다.
        Optional<Map<String, Object>> moduleOpt = repo.findCourseModuleForHomework(courseId, homeworkId, auth.siteId());
        if (moduleOpt.isEmpty()) {
            result.put("rst_code", TutorApiResponse.CODE_NOT_FOUND);
            result.put("rst_message", "해당 과제가 과목에 배치되어 있지 않습니다.");
            return result;
        }

        if (!repo.courseUserExists(courseUserId, courseId, auth.siteId())) {
            result.put("rst_code", TutorApiResponse.CODE_USER_NOT_FOUND);
            result.put("rst_message", "해당 수강 정보가 없습니다.");
            return result;
        }

        // 왜: base64 이미지 검증
        if (feedback != null && containsBase64Image(feedback)) {
            result.put("rst_code", "1101");
            result.put("rst_message", "이미지는 첨부파일로 업로드해 주세요.");
            return result;
        }

        double assignScore = ((Number) moduleOpt.get().get("assign_score")).doubleValue();
        // 왜: marking_score(0-100%) x assign_score = final_score 변환
        double score = Math.min(assignScore, assignScore * markingScore / 100.0);
        String now = LocalDateTime.now().format(DATETIME_FORMAT);

        // 왜: 기존 제출이 있으면 수정, 없으면 신규 생성합니다.
        //     오프라인 과제(또는 별도 제출경로)도 채점이 가능해야 하므로, 없으면 레코드를 생성합니다.
        Optional<Map<String, Object>> existingOpt = repo.findHomeworkUser(homeworkId, courseUserId);
        boolean saved;
        if (existingOpt.isPresent()) {
            saved = repo.updateHomeworkUserFeedback(
                    homeworkId, courseUserId, auth.userId(), markingScore, score, feedback, now);
        } else {
            // 왜: courseUserId로 user_id를 조회합니다.
            int studentUserId = repo.findUserIdByCourseUserId(courseUserId, auth.siteId());
            saved = repo.insertHomeworkUser(
                    homeworkId, courseUserId, courseId, studentUserId, auth.siteId(),
                    auth.userId(), markingScore, score, feedback, now);
        }

        if (!saved) {
            result.put("rst_code", TutorApiResponse.CODE_DB_FAIL);
            result.put("rst_message", "저장 중 오류가 발생했습니다.");
            return result;
        }

        // 왜: 성적 반영 - courseUser.setCourseUserScore(courseUserId, "homework") 재현
        repo.recalculateCourseUserHomeworkScore(courseUserId, auth.siteId());

        log.info("과제 피드백 저장: homeworkId={}, courseUserId={}, markingScore={}", homeworkId, courseUserId, markingScore);

        result.put("rst_code", TutorApiResponse.CODE_SUCCESS);
        result.put("rst_message", "성공");
        result.put("rst_data", courseUserId);
        return result;
    }

    // ========== homework_submissions.jsp ==========

    public Map<String, Object> listSubmissions(TutorAuthContext.AuthInfo auth,
                                                String keyword, String startDate, String endDate,
                                                String status, int page, int pageSize) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 왜: 페이지 범위 보정 (JSP 로직 재현)
        if (page < 1) page = 1;
        if (pageSize != 20 && pageSize != 50 && pageSize != 100) pageSize = 20;
        int offset = (page - 1) * pageSize;

        // 왜: 날짜 형식 변환 (yyyy-MM-dd 또는 yyyy.MM.dd → yyyyMMdd)
        String startDateFormatted = normalizeDate(startDate);
        String endDateFormatted = normalizeDate(endDate);

        int totalCount = repo.countSubmissions(
                auth.userId(), auth.siteId(), auth.isAdmin(),
                keyword, startDateFormatted, endDateFormatted, status);

        List<Map<String, Object>> list = repo.listSubmissions(
                auth.userId(), auth.siteId(), auth.isAdmin(),
                keyword, startDateFormatted, endDateFormatted, status,
                offset, pageSize);

        // 왜: JSP에서 while(list.next()) 루프 안에서 하던 포맷 가공을 재현합니다.
        for (Map<String, Object> row : list) {
            String submitDate = str(row, "submit_date");
            row.put("submitted_at", !submitDate.isEmpty() ? formatDateTime(submitDate) : "-");
            row.put("confirmed", "Y".equals(str(row, "confirm_yn")));
        }

        result.put("rst_code", TutorApiResponse.CODE_SUCCESS);
        result.put("rst_message", "성공");
        result.put("rst_total", totalCount);
        result.put("rst_page", page);
        result.put("rst_limit", pageSize);
        result.put("rst_data", list);
        return result;
    }

    // ========== homework_submit_cancel.jsp ==========

    public Map<String, Object> cancelSubmission(TutorAuthContext.AuthInfo auth, int courseId,
                                                 int homeworkId, int courseUserId) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (courseId == 0 || homeworkId == 0 || courseUserId == 0) {
            result.put("rst_code", TutorApiResponse.CODE_MISSING_PARAM);
            result.put("rst_message", "course_id, homework_id, course_user_id가 필요합니다.");
            return result;
        }

        if (!canAccessCourse(auth, courseId)) {
            result.put("rst_code", TutorApiResponse.CODE_NO_EDIT_PERMISSION);
            result.put("rst_message", "해당 과목의 과제 제출을 취소할 권한이 없습니다.");
            return result;
        }

        if (!repo.courseUserExists(courseUserId, courseId, auth.siteId())) {
            result.put("rst_code", TutorApiResponse.CODE_USER_NOT_FOUND);
            result.put("rst_message", "해당 수강 정보가 없습니다.");
            return result;
        }

        // 왜: 제출 기록이 없으면 취소할 대상이 없으므로 안내만 합니다.
        Optional<Map<String, Object>> huOpt = repo.findHomeworkUser(homeworkId, courseUserId);
        if (huOpt.isEmpty()) {
            result.put("rst_code", "4042");
            result.put("rst_message", "해당 제출 정보가 없습니다.");
            return result;
        }

        boolean deleted = repo.deleteHomeworkUser(homeworkId, courseUserId);
        if (!deleted) {
            result.put("rst_code", TutorApiResponse.CODE_DB_FAIL);
            result.put("rst_message", "제출 취소 중 오류가 발생했습니다.");
            return result;
        }

        // 왜: 추가과제/첨부파일이 남으면 재제출 때 혼란이 생기므로 함께 정리합니다.
        repo.deleteHomeworkTasks(homeworkId, courseUserId);
        repo.deleteHomeworkFiles(homeworkId, courseUserId);

        // 왜: 성적 반영
        repo.recalculateCourseUserHomeworkScore(courseUserId, auth.siteId());

        log.info("과제 제출취소: homeworkId={}, courseUserId={}", homeworkId, courseUserId);

        result.put("rst_code", TutorApiResponse.CODE_SUCCESS);
        result.put("rst_message", "성공");
        result.put("rst_data", courseUserId);
        return result;
    }

    // ========== homework_task_append.jsp ==========

    public Map<String, Object> appendTask(TutorAuthContext.AuthInfo auth, int courseId,
                                           int homeworkId, int courseUserId, String task) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (courseId == 0 || homeworkId == 0 || courseUserId == 0) {
            result.put("rst_code", TutorApiResponse.CODE_MISSING_PARAM);
            result.put("rst_message", "course_id, homework_id, course_user_id가 필요합니다.");
            return result;
        }
        if (task == null || task.isEmpty()) {
            result.put("rst_code", "1002");
            result.put("rst_message", "task(추가 과제 내용)가 필요합니다.");
            return result;
        }

        if (!canAccessCourse(auth, courseId)) {
            result.put("rst_code", TutorApiResponse.CODE_NO_EDIT_PERMISSION);
            result.put("rst_message", "해당 과목의 추가 과제를 부여할 권한이 없습니다.");
            return result;
        }

        // 왜: 다른 과목의 과제ID로 임의 호출되는 것을 막습니다.
        if (!repo.courseModuleExists(courseId, homeworkId)) {
            result.put("rst_code", TutorApiResponse.CODE_NOT_FOUND);
            result.put("rst_message", "해당 과제가 과목에 배치되어 있지 않습니다.");
            return result;
        }

        if (!repo.courseUserExists(courseUserId, courseId, auth.siteId())) {
            result.put("rst_code", TutorApiResponse.CODE_USER_NOT_FOUND);
            result.put("rst_message", "해당 수강 정보가 없습니다.");
            return result;
        }

        // 왜: base64 이미지 검증
        if (containsBase64Image(task)) {
            result.put("rst_code", "1101");
            result.put("rst_message", "이미지는 첨부파일로 업로드해 주세요.");
            return result;
        }
        int bytes = getByteLength(task);
        if (bytes > 60000) {
            result.put("rst_code", "1102");
            result.put("rst_message", "내용은 60000바이트를 초과할 수 없습니다. (현재 " + bytes + "바이트)");
            return result;
        }

        // 왜: parent_id는 "직전 추가과제"를 가리키게 해서 타임라인을 만들 수 있게 합니다.
        int parentId = repo.findLastTaskId(auth.siteId(), courseId, homeworkId, courseUserId);

        int studentUserId = repo.findUserIdByCourseUserId(courseUserId, auth.siteId());
        String now = LocalDateTime.now().format(DATETIME_FORMAT);

        long newId = repo.insertHomeworkTask(
                auth.siteId(), courseId, homeworkId, courseUserId, studentUserId,
                parentId, auth.userId(), task, now);

        if (newId <= 0) {
            result.put("rst_code", TutorApiResponse.CODE_DB_FAIL);
            result.put("rst_message", "추가 과제 저장 중 오류가 발생했습니다.");
            return result;
        }

        log.info("추가 과제 부여: taskId={}, homeworkId={}, courseUserId={}", newId, homeworkId, courseUserId);

        result.put("rst_code", TutorApiResponse.CODE_SUCCESS);
        result.put("rst_message", "성공");
        result.put("rst_data", newId);
        return result;
    }

    // ========== homework_task_confirm.jsp ==========

    public Map<String, Object> confirmTask(TutorAuthContext.AuthInfo auth, int courseId,
                                            int taskId, String feedback) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (taskId == 0 || courseId == 0) {
            result.put("rst_code", TutorApiResponse.CODE_MISSING_PARAM);
            result.put("rst_message", "task_id, course_id가 필요합니다.");
            return result;
        }

        if (!canAccessCourse(auth, courseId)) {
            result.put("rst_code", TutorApiResponse.CODE_NO_EDIT_PERMISSION);
            result.put("rst_message", "해당 추가 과제를 평가할 권한이 없습니다.");
            return result;
        }

        if (!repo.homeworkTaskExists(taskId, courseId)) {
            result.put("rst_code", TutorApiResponse.CODE_NOT_FOUND);
            result.put("rst_message", "해당 추가 과제 정보를 찾을 수 없습니다.");
            return result;
        }

        String now = LocalDateTime.now().format(DATETIME_FORMAT);
        boolean updated = repo.confirmHomeworkTask(taskId, auth.userId(), feedback, now);
        if (!updated) {
            result.put("rst_code", TutorApiResponse.CODE_DB_FAIL);
            result.put("rst_message", "추가 과제 정보 업데이트 중 오류가 발생했습니다.");
            return result;
        }

        log.info("추가 과제 확인: taskId={}, courseId={}", taskId, courseId);

        result.put("rst_code", TutorApiResponse.CODE_SUCCESS);
        result.put("rst_message", "성공");
        return result;
    }

    // ========== homework_task_list.jsp ==========

    public Map<String, Object> listTasks(TutorAuthContext.AuthInfo auth, int courseId,
                                          int homeworkId, int courseUserId) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (courseId == 0 || homeworkId == 0 || courseUserId == 0) {
            result.put("rst_code", TutorApiResponse.CODE_MISSING_PARAM);
            result.put("rst_message", "course_id, homework_id, course_user_id가 필요합니다.");
            return result;
        }

        if (!canAccessCourse(auth, courseId)) {
            result.put("rst_code", TutorApiResponse.CODE_NO_EDIT_PERMISSION);
            result.put("rst_message", "해당 과목의 추가 과제를 조회할 권한이 없습니다.");
            return result;
        }

        List<Map<String, Object>> list = repo.listHomeworkTasks(auth.siteId(), courseId, homeworkId, courseUserId);

        // 왜: JSP에서 while(list.next()) 루프 안에서 하던 포맷 가공을 재현합니다.
        for (Map<String, Object> row : list) {
            String taskContent = str(row, "task");
            row.put("task_preview", taskContent.length() > 50 ? taskContent.substring(0, 50) + "..." : taskContent);
            row.put("submit_yn_label", "Y".equals(str(row, "submit_yn")) ? "제출완료" : "미제출");
            row.put("confirm_yn_label", "Y".equals(str(row, "confirm_yn")) ? "평가완료" : "평가대기");
            row.put("submit_date_conv", formatDateTime(str(row, "submit_date")));
            row.put("confirm_date_conv", formatDateTime(str(row, "confirm_date")));
            row.put("reg_date_conv", formatDateTime(str(row, "reg_date")));

            // 왜: 재제출 상태: submit_yn=Y이고 confirm_yn=N인 경우 재평가 필요
            boolean needReview = "Y".equals(str(row, "submit_yn")) && !"Y".equals(str(row, "confirm_yn"));
            row.put("need_review", needReview);
        }

        result.put("rst_code", TutorApiResponse.CODE_SUCCESS);
        result.put("rst_message", "성공");
        result.put("rst_data", list);
        return result;
    }

    // ========== homework_users.jsp ==========

    public Map<String, Object> listHomeworkUsers(TutorAuthContext.AuthInfo auth, int courseId, int homeworkId) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (courseId == 0 || homeworkId == 0) {
            result.put("rst_code", TutorApiResponse.CODE_MISSING_PARAM);
            result.put("rst_message", "course_id, homework_id가 필요합니다.");
            return result;
        }

        if (!canAccessCourse(auth, courseId)) {
            result.put("rst_code", TutorApiResponse.CODE_NO_EDIT_PERMISSION);
            result.put("rst_message", "해당 과목의 과제 제출현황을 조회할 권한이 없습니다.");
            return result;
        }

        // 왜: 과목에 배치된 과제의 assign_score, homework 정보를 함께 조회합니다.
        Optional<Map<String, Object>> moduleOpt = repo.findCourseModuleWithHomeworkInfo(courseId, homeworkId, auth.siteId());
        if (moduleOpt.isEmpty()) {
            result.put("rst_code", TutorApiResponse.CODE_NOT_FOUND);
            result.put("rst_message", "해당 과제가 과목에 배치되어 있지 않습니다.");
            return result;
        }

        Map<String, Object> moduleInfo = moduleOpt.get();
        double assignScore = ((Number) moduleInfo.get("assign_score")).doubleValue();

        List<Map<String, Object>> list = repo.listHomeworkUsers(courseId, homeworkId, auth.siteId());

        // 왜: JSP에서 while(list.next()) 루프 안에서 하던 포맷 가공을 재현합니다.
        for (Map<String, Object> row : list) {
            boolean submitted = "Y".equals(str(row, "submit_yn"));
            row.put("submitted", submitted);
            String submitDate = str(row, "submit_date");
            row.put("submitted_at", submitted && !submitDate.isEmpty() ? formatDateTime(submitDate) : "-");
            row.put("confirm", "Y".equals(str(row, "confirm_yn")));
            row.put("confirm_at", formatDateTime(str(row, "confirm_date")));

            double marking = row.get("marking_score") != null
                    ? ((Number) row.get("marking_score")).doubleValue() : 0.0;
            double convScore = Math.min(assignScore, assignScore * marking / 100.0);
            row.put("marking_score_conv", String.format("%.0f", marking));
            row.put("score_conv", String.format("%.2f", convScore));
        }

        result.put("rst_code", TutorApiResponse.CODE_SUCCESS);
        result.put("rst_message", "성공");
        result.put("rst_data", list);
        result.put("rst_homework", moduleInfo);
        return result;
    }

    // ==================== 내부 유틸리티 ====================

    private String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v != null ? v.toString().trim() : "";
    }

    /**
     * 왜: JSP의 m.time("yyyy.MM.dd HH:mm", dateStr)을 재현합니다.
     *     yyyyMMddHHmmss → yyyy.MM.dd HH:mm 변환.
     */
    private String formatDateTime(String yyyymmddhhmmss) {
        if (yyyymmddhhmmss == null || yyyymmddhhmmss.isEmpty()) return "-";
        // 왜: 최소 12자리(yyyyMMddHHmm)가 있어야 변환 가능합니다.
        if (yyyymmddhhmmss.length() >= 12) {
            return yyyymmddhhmmss.substring(0, 4) + "."
                    + yyyymmddhhmmss.substring(4, 6) + "."
                    + yyyymmddhhmmss.substring(6, 8) + " "
                    + yyyymmddhhmmss.substring(8, 10) + ":"
                    + yyyymmddhhmmss.substring(10, 12);
        } else if (yyyymmddhhmmss.length() >= 8) {
            return yyyymmddhhmmss.substring(0, 4) + "."
                    + yyyymmddhhmmss.substring(4, 6) + "."
                    + yyyymmddhhmmss.substring(6, 8);
        }
        return yyyymmddhhmmss;
    }

    /**
     * 왜: JSP의 m.time("yyyyMMdd", dueDate) + dueTime 변환을 재현합니다.
     *     dueDate (yyyy-MM-dd 또는 yyyyMMdd) + dueTime (HH:mm) → yyyyMMddHHmmss
     */
    private String buildEndDateTime(String dueDate, String dueTime) {
        String endYmd = normalizeDate(dueDate);
        String endH = "23";
        String endM = "59";
        if (dueTime != null && dueTime.length() >= 5) {
            endH = dueTime.substring(0, 2);
            endM = dueTime.substring(3, 5);
        }
        return endYmd + endH + endM + "59";
    }

    /**
     * 왜: 날짜 문자열에서 구분자를 제거하여 yyyyMMdd 형식으로 정규화합니다.
     */
    private String normalizeDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "";
        return dateStr.replace("-", "").replace(".", "").replace("/", "");
    }
}
