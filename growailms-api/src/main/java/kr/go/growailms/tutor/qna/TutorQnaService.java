package kr.go.growailms.tutor.qna;

import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 왜: qna_*.jsp 4개의 비즈니스 로직을 모아둔 서비스입니다.
 *     QnA는 CL_POST 테이블에 저장되고, CL_BOARD로 게시판 유형을 구분합니다.
 *     답변은 부모 글(parent_id)로 연결되거나, 답변 필드(answer_content, answer_user_id)에 저장됩니다.
 */
@Service
public class TutorQnaService {

    private static final Logger log = LoggerFactory.getLogger(TutorQnaService.class);

    private final TutorQnaJdbcRepository repo;

    public TutorQnaService(TutorQnaJdbcRepository repo) {
        this.repo = repo;
    }

    // ========== 권한 확인 공통 메서드 ==========

    private boolean canAccessCourse(TutorAuthContext.AuthInfo auth, int courseId) {
        // 왜: 관리자는 전체 과목, 교수자는 주담당 과목만 접근 가능합니다.
        return auth.isAdmin() || repo.isMajorTutor(auth.userId(), courseId, auth.siteId());
    }

    // ========== qna_list.jsp ==========

    public List<Map<String, Object>> listQna(TutorAuthContext.AuthInfo auth, int courseId,
                                              String keyword, String answerStatus,
                                              int page, int pageSize) {
        if (!canAccessCourse(auth, courseId)) {
            return List.of();
        }

        List<Map<String, Object>> rows = repo.listQna(courseId, auth.siteId(), keyword,
                answerStatus, page, pageSize);

        // 왜: JSP에서 하던 화면용 가공(날짜 포맷, 답변 상태 라벨)을 서비스에서 수행합니다.
        for (Map<String, Object> row : rows) {
            formatQnaRow(row);
        }
        return rows;
    }

    // ========== qna_view.jsp ==========

    public Optional<Map<String, Object>> getQnaDetail(TutorAuthContext.AuthInfo auth, int postId) {
        Optional<Map<String, Object>> postOpt = repo.getQnaPost(postId, auth.siteId());
        if (postOpt.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> post = postOpt.get();
        // 왜: QnA 상세에서는 과목 접근 권한도 확인합니다.
        Object courseIdObj = post.get("course_id");
        if (courseIdObj != null) {
            int courseId = ((Number) courseIdObj).intValue();
            if (courseId > 0 && !canAccessCourse(auth, courseId)) {
                return Optional.empty();
            }
        }

        formatQnaRow(post);

        // 왜: 답글(comments) 목록도 함께 반환합니다.
        List<Map<String, Object>> replies = repo.listReplies(postId, auth.siteId());
        for (Map<String, Object> reply : replies) {
            formatQnaRow(reply);
        }
        post.put("replies", replies);

        return Optional.of(post);
    }

    // ========== qna_answer.jsp ==========

    public boolean answerQna(TutorAuthContext.AuthInfo auth, int postId, String content) {
        // 왜: 원글의 과목 ID를 확인하여 권한 체크합니다.
        Optional<Map<String, Object>> postOpt = repo.getQnaPost(postId, auth.siteId());
        if (postOpt.isEmpty()) {
            return false;
        }

        Map<String, Object> post = postOpt.get();
        Object courseIdObj = post.get("course_id");
        if (courseIdObj != null) {
            int courseId = ((Number) courseIdObj).intValue();
            if (courseId > 0 && !canAccessCourse(auth, courseId)) {
                return false;
            }
        }

        // 왜: 답변은 원글의 answer_content, answer_user_id, answer_date 필드를 업데이트합니다.
        int updated = repo.updateAnswer(postId, auth.userId(), content, auth.siteId());
        if (updated > 0) {
            log.info("QnA 답변 작성: postId={}, userId={}", postId, auth.userId());
        }
        return updated > 0;
    }

    // ========== qna_manage_list.jsp ==========

    public List<Map<String, Object>> manageListQna(TutorAuthContext.AuthInfo auth, String keyword,
                                                    String answerStatus, int courseId,
                                                    int page, int pageSize) {
        // 왜: 관리 목록은 교수자의 전체 담당 과목에 걸친 QnA를 조회합니다.
        List<Map<String, Object>> rows = repo.manageListQna(
                auth.userId(), auth.siteId(), auth.isAdmin(),
                keyword, answerStatus, courseId, page, pageSize);

        for (Map<String, Object> row : rows) {
            formatQnaRow(row);
        }
        return rows;
    }

    // ==================== 내부 유틸리티 ====================

    private void formatQnaRow(Map<String, Object> row) {
        // 왜: 답변 상태 라벨을 추가합니다.
        String answerContent = str(row, "answer_content");
        row.put("answer_status_conv", answerContent.isEmpty() ? "미답변" : "답변완료");

        // 왜: 제목 자르기
        row.put("title_conv", cutString(str(row, "title"), 80));

        // 왜: 작성자 이름 (TB_USER JOIN)
        String userNm = str(row, "user_nm");
        row.put("writer_nm", userNm.isEmpty() ? "알수없음" : userNm);
    }

    private String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v != null ? v.toString().trim() : "";
    }

    private String cutString(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
