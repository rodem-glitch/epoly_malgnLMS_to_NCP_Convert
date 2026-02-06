package kr.go.growailms.tutor.question;

import kr.go.growailms.tutor.common.TutorApiResponse;
import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 왜: tutor_lms/api/question_*.jsp 8개를 하나의 REST 컨트롤러로 통합합니다.
 *     init.jsp 인증은 TutorAuthInterceptor가 처리하므로, 여기서는 비즈니스 로직만 담당합니다.
 *
 * 엔드포인트 매핑:
 *   question_bank_list.jsp        → GET    /api/tutor/questions
 *   question_bank_insert.jsp      → POST   /api/tutor/questions
 *   question_bank_modify.jsp      → POST   /api/tutor/questions/{id}
 *   question_bank_delete.jsp      → POST   /api/tutor/questions/{id}/delete
 *   question_category_list.jsp    → GET    /api/tutor/question-categories
 *   question_category_insert.jsp  → POST   /api/tutor/question-categories
 *   question_category_modify.jsp  → POST   /api/tutor/question-categories/{id}
 *   question_category_delete.jsp  → POST   /api/tutor/question-categories/{id}/delete
 */
@RestController
@RequestMapping("/api/tutor")
public class TutorQuestionController {

    private static final Logger log = LoggerFactory.getLogger(TutorQuestionController.class);

    private final TutorQuestionService questionService;

    public TutorQuestionController(TutorQuestionService questionService) {
        this.questionService = questionService;
    }

    // ==================== 문제 은행 (LM_QUESTION) ====================

    // ========== question_bank_list.jsp ==========
    @GetMapping("/questions")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listQuestions(
            @RequestParam(name = "category_id", defaultValue = "0") int categoryId,
            @RequestParam(name = "question_type", defaultValue = "") String questionType,
            @RequestParam(name = "s_keyword", defaultValue = "") String keyword,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("문제 은행 목록: userId={}, categoryId={}", auth.userId(), categoryId);

        List<Map<String, Object>> list = questionService.listQuestions(auth, categoryId,
                questionType, keyword, page, pageSize);
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }

    // ========== question_bank_insert.jsp ==========
    @PostMapping("/questions")
    public ResponseEntity<TutorApiResponse<Long>> insertQuestion(
            @RequestParam(name = "category_id", defaultValue = "0") int categoryId,
            @RequestParam(name = "question_type") String questionType,
            @RequestParam(name = "title") String title,
            @RequestParam(name = "content", defaultValue = "") String content,
            @RequestParam(name = "answer", defaultValue = "") String answer,
            @RequestParam(name = "score", defaultValue = "0") int score,
            @RequestParam(name = "difficulty", defaultValue = "1") int difficulty,
            @RequestParam(name = "options", defaultValue = "") String options,
            @RequestParam(name = "explanation", defaultValue = "") String explanation
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("문제 등록: userId={}, title={}", auth.userId(), title);

        long newId = questionService.insertQuestion(auth, categoryId, questionType, title,
                content, answer, score, difficulty, options, explanation);
        return ResponseEntity.ok(TutorApiResponse.success(newId));
    }

    // ========== question_bank_modify.jsp ==========
    @PostMapping("/questions/{id}")
    public ResponseEntity<TutorApiResponse<Integer>> modifyQuestion(
            @PathVariable("id") int id,
            @RequestParam(name = "category_id", defaultValue = "0") int categoryId,
            @RequestParam(name = "question_type", defaultValue = "") String questionType,
            @RequestParam(name = "title") String title,
            @RequestParam(name = "content", defaultValue = "") String content,
            @RequestParam(name = "answer", defaultValue = "") String answer,
            @RequestParam(name = "score", defaultValue = "0") int score,
            @RequestParam(name = "difficulty", defaultValue = "1") int difficulty,
            @RequestParam(name = "options", defaultValue = "") String options,
            @RequestParam(name = "explanation", defaultValue = "") String explanation
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("문제 수정: userId={}, questionId={}", auth.userId(), id);

        boolean ok = questionService.modifyQuestion(auth, id, categoryId, questionType, title,
                content, answer, score, difficulty, options, explanation);
        if (!ok) {
            return ResponseEntity.ok(TutorApiResponse.error(
                    TutorApiResponse.CODE_NO_EDIT_PERMISSION, "문제 수정 권한이 없습니다."));
        }
        return ResponseEntity.ok(TutorApiResponse.success(id));
    }

    // ========== question_bank_delete.jsp ==========
    @PostMapping("/questions/{id}/delete")
    public ResponseEntity<TutorApiResponse<Integer>> deleteQuestion(
            @PathVariable("id") int id
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("문제 삭제: userId={}, questionId={}", auth.userId(), id);

        boolean ok = questionService.deleteQuestion(auth, id);
        if (!ok) {
            return ResponseEntity.ok(TutorApiResponse.error(
                    TutorApiResponse.CODE_NO_EDIT_PERMISSION, "문제 삭제 권한이 없습니다."));
        }
        return ResponseEntity.ok(TutorApiResponse.success(id));
    }

    // ==================== 문제 카테고리 (LM_QUESTION_CATEGORY) ====================

    // ========== question_category_list.jsp ==========
    @GetMapping("/question-categories")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listCategories(
            @RequestParam(name = "parent_id", defaultValue = "0") int parentId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("문제 카테고리 목록: userId={}", auth.userId());

        List<Map<String, Object>> list = questionService.listCategories(auth, parentId);
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }

    // ========== question_category_insert.jsp ==========
    @PostMapping("/question-categories")
    public ResponseEntity<TutorApiResponse<Long>> insertCategory(
            @RequestParam(name = "category_nm") String categoryNm,
            @RequestParam(name = "parent_id", defaultValue = "0") int parentId,
            @RequestParam(name = "sort", defaultValue = "0") int sort
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("문제 카테고리 등록: userId={}, categoryNm={}", auth.userId(), categoryNm);

        long newId = questionService.insertCategory(auth, categoryNm, parentId, sort);
        return ResponseEntity.ok(TutorApiResponse.success(newId));
    }

    // ========== question_category_modify.jsp ==========
    @PostMapping("/question-categories/{id}")
    public ResponseEntity<TutorApiResponse<Integer>> modifyCategory(
            @PathVariable("id") int id,
            @RequestParam(name = "category_nm") String categoryNm,
            @RequestParam(name = "sort", defaultValue = "0") int sort
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("문제 카테고리 수정: userId={}, categoryId={}", auth.userId(), id);

        boolean ok = questionService.modifyCategory(auth, id, categoryNm, sort);
        if (!ok) {
            return ResponseEntity.ok(TutorApiResponse.error(
                    TutorApiResponse.CODE_NO_EDIT_PERMISSION, "카테고리 수정 권한이 없습니다."));
        }
        return ResponseEntity.ok(TutorApiResponse.success(id));
    }

    // ========== question_category_delete.jsp ==========
    @PostMapping("/question-categories/{id}/delete")
    public ResponseEntity<TutorApiResponse<Integer>> deleteCategory(
            @PathVariable("id") int id
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("문제 카테고리 삭제: userId={}, categoryId={}", auth.userId(), id);

        boolean ok = questionService.deleteCategory(auth, id);
        if (!ok) {
            return ResponseEntity.ok(TutorApiResponse.error(
                    TutorApiResponse.CODE_NO_EDIT_PERMISSION, "카테고리 삭제 권한이 없습니다."));
        }
        return ResponseEntity.ok(TutorApiResponse.success(id));
    }
}
