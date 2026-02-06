package kr.go.growailms.tutor.qna;

import kr.go.growailms.tutor.common.TutorApiResponse;
import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 왜: tutor_lms/api/qna_*.jsp 4개를 하나의 REST 컨트롤러로 통합합니다.
 *     init.jsp 인증은 TutorAuthInterceptor가 처리하므로, 여기서는 비즈니스 로직만 담당합니다.
 *
 * 엔드포인트 매핑:
 *   qna_list.jsp        → GET  /api/tutor/qna?course_id=
 *   qna_view.jsp        → GET  /api/tutor/qna/{postId}
 *   qna_answer.jsp      → POST /api/tutor/qna/{postId}/answer
 *   qna_manage_list.jsp → GET  /api/tutor/qna/manage
 */
@RestController
@RequestMapping("/api/tutor/qna")
public class TutorQnaController {

    private static final Logger log = LoggerFactory.getLogger(TutorQnaController.class);

    private final TutorQnaService qnaService;

    public TutorQnaController(TutorQnaService qnaService) {
        this.qnaService = qnaService;
    }

    // ========== qna_list.jsp ==========
    @GetMapping
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listQna(
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "s_keyword", defaultValue = "") String keyword,
            @RequestParam(name = "answer_status", defaultValue = "") String answerStatus,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("QnA 목록 조회: userId={}, courseId={}", auth.userId(), courseId);

        List<Map<String, Object>> list = qnaService.listQna(auth, courseId, keyword, answerStatus, page, pageSize);
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }

    // ========== qna_view.jsp ==========
    @GetMapping("/{postId}")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> viewQna(
            @PathVariable("postId") int postId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("QnA 상세 조회: userId={}, postId={}", auth.userId(), postId);

        return qnaService.getQnaDetail(auth, postId)
                .map(data -> ResponseEntity.ok(TutorApiResponse.success(data)))
                .orElse(ResponseEntity.ok(TutorApiResponse.notFound("QnA 게시글")));
    }

    // ========== qna_answer.jsp ==========
    @PostMapping("/{postId}/answer")
    public ResponseEntity<TutorApiResponse<Integer>> answerQna(
            @PathVariable("postId") int postId,
            @RequestParam(name = "content") String content
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("QnA 답변 작성: userId={}, postId={}", auth.userId(), postId);

        boolean ok = qnaService.answerQna(auth, postId, content);
        if (!ok) {
            return ResponseEntity.ok(TutorApiResponse.error(
                    TutorApiResponse.CODE_NO_EDIT_PERMISSION, "QnA 답변 권한이 없습니다."));
        }
        return ResponseEntity.ok(TutorApiResponse.success(postId));
    }

    // ========== qna_manage_list.jsp ==========
    @GetMapping("/manage")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> manageListQna(
            @RequestParam(name = "s_keyword", defaultValue = "") String keyword,
            @RequestParam(name = "answer_status", defaultValue = "") String answerStatus,
            @RequestParam(name = "course_id", defaultValue = "0") int courseId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("QnA 관리 목록: userId={}", auth.userId());

        List<Map<String, Object>> list = qnaService.manageListQna(auth, keyword, answerStatus,
                courseId, page, pageSize);
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }
}
