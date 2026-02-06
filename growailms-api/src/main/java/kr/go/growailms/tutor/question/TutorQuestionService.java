package kr.go.growailms.tutor.question;

import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 왜: question_*.jsp 8개의 비즈니스 로직을 모아둔 서비스입니다.
 *     문제 은행(LM_QUESTION)과 문제 카테고리(LM_QUESTION_CATEGORY)의 CRUD를 처리합니다.
 *     교수자는 본인이 등록한 문제만, 관리자는 전체 문제를 관리할 수 있습니다.
 */
@Service
public class TutorQuestionService {

    private static final Logger log = LoggerFactory.getLogger(TutorQuestionService.class);

    private final TutorQuestionJdbcRepository repo;

    public TutorQuestionService(TutorQuestionJdbcRepository repo) {
        this.repo = repo;
    }

    // ==================== 문제 은행 ====================

    // ========== question_bank_list.jsp ==========

    public List<Map<String, Object>> listQuestions(TutorAuthContext.AuthInfo auth, int categoryId,
                                                    String questionType, String keyword,
                                                    int page, int pageSize) {
        // 왜: 교수자는 본인이 등록한 문제 + 공개 문제, 관리자는 전체를 조회합니다.
        return repo.listQuestions(auth.userId(), auth.siteId(), auth.isAdmin(),
                categoryId, questionType, keyword, page, pageSize);
    }

    // ========== question_bank_insert.jsp ==========

    public long insertQuestion(TutorAuthContext.AuthInfo auth, int categoryId, String questionType,
                               String title, String content, String answer, int score,
                               int difficulty, String options, String explanation) {
        long newId = repo.insertQuestion(auth.userId(), auth.siteId(), categoryId, questionType,
                title, content, answer, score, difficulty, options, explanation);
        log.info("문제 등록: id={}, title={}, userId={}", newId, title, auth.userId());
        return newId;
    }

    // ========== question_bank_modify.jsp ==========

    public boolean modifyQuestion(TutorAuthContext.AuthInfo auth, int questionId, int categoryId,
                                  String questionType, String title, String content, String answer,
                                  int score, int difficulty, String options, String explanation) {
        // 왜: 교수자는 본인이 등록한 문제만 수정 가능합니다.
        if (!canEditQuestion(auth, questionId)) {
            return false;
        }
        int updated = repo.updateQuestion(questionId, auth.siteId(), categoryId, questionType,
                title, content, answer, score, difficulty, options, explanation);
        if (updated > 0) {
            log.info("문제 수정: questionId={}, userId={}", questionId, auth.userId());
        }
        return updated > 0;
    }

    // ========== question_bank_delete.jsp ==========

    public boolean deleteQuestion(TutorAuthContext.AuthInfo auth, int questionId) {
        if (!canEditQuestion(auth, questionId)) {
            return false;
        }
        int updated = repo.softDeleteQuestion(questionId, auth.siteId());
        if (updated > 0) {
            log.info("문제 삭제: questionId={}, userId={}", questionId, auth.userId());
        }
        return updated > 0;
    }

    // ==================== 문제 카테고리 ====================

    // ========== question_category_list.jsp ==========

    public List<Map<String, Object>> listCategories(TutorAuthContext.AuthInfo auth, int parentId) {
        return repo.listCategories(auth.siteId(), parentId);
    }

    // ========== question_category_insert.jsp ==========

    public long insertCategory(TutorAuthContext.AuthInfo auth, String categoryNm, int parentId, int sort) {
        // 왜: 카테고리 depth는 부모의 depth + 1로 자동 계산합니다.
        int depth = 1;
        if (parentId > 0) {
            depth = repo.getCategoryDepth(parentId, auth.siteId()) + 1;
        }
        if (sort <= 0) {
            sort = repo.calcNextCategorySort(auth.siteId(), parentId);
        }
        long newId = repo.insertCategory(auth.siteId(), categoryNm, parentId, depth, sort);
        log.info("문제 카테고리 등록: id={}, categoryNm={}, userId={}", newId, categoryNm, auth.userId());
        return newId;
    }

    // ========== question_category_modify.jsp ==========

    public boolean modifyCategory(TutorAuthContext.AuthInfo auth, int categoryId, String categoryNm, int sort) {
        int updated = repo.updateCategory(categoryId, auth.siteId(), categoryNm, sort);
        if (updated > 0) {
            log.info("문제 카테고리 수정: categoryId={}, userId={}", categoryId, auth.userId());
        }
        return updated > 0;
    }

    // ========== question_category_delete.jsp ==========

    public boolean deleteCategory(TutorAuthContext.AuthInfo auth, int categoryId) {
        // 왜: 하위 카테고리가 있으면 삭제할 수 없습니다.
        int childCount = repo.countChildCategories(categoryId, auth.siteId());
        if (childCount > 0) {
            log.warn("카테고리 삭제 실패: 하위 카테고리 {}건 존재 (categoryId={})", childCount, categoryId);
            return false;
        }

        // 왜: 해당 카테고리에 문제가 있으면 삭제할 수 없습니다.
        int questionCount = repo.countQuestionsByCategory(categoryId, auth.siteId());
        if (questionCount > 0) {
            log.warn("카테고리 삭제 실패: 문제 {}건 존재 (categoryId={})", questionCount, categoryId);
            return false;
        }

        int updated = repo.softDeleteCategory(categoryId, auth.siteId());
        if (updated > 0) {
            log.info("문제 카테고리 삭제: categoryId={}, userId={}", categoryId, auth.userId());
        }
        return updated > 0;
    }

    // ==================== 내부 유틸리티 ====================

    private boolean canEditQuestion(TutorAuthContext.AuthInfo auth, int questionId) {
        // 왜: 관리자는 모든 문제 수정 가능, 교수자는 본인 등록 문제만 수정 가능합니다.
        return auth.isAdmin() || repo.isQuestionOwner(questionId, auth.userId(), auth.siteId());
    }
}
