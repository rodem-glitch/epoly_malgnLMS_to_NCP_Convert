package kr.go.growailms.tutor.question;

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

/**
 * 왜: 레거시 DAO(QuestionDao, QuestionCategoryDao)가 하던 DB 접근을
 *     Spring Boot의 NamedParameterJdbcTemplate으로 대체합니다.
 *     테이블: LM_QUESTION, LM_QUESTION_CATEGORY
 */
@Repository
public class TutorQuestionJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(TutorQuestionJdbcRepository.class);
    private final NamedParameterJdbcTemplate jdbc;

    public TutorQuestionJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 문제 은행 (LM_QUESTION) ====================

    public List<Map<String, Object>> listQuestions(long userId, long siteId, boolean isAdmin,
                                                    int categoryId, String questionType,
                                                    String keyword, int page, int pageSize) {
        StringBuilder sql = new StringBuilder("""
                SELECT q.id, q.category_id, q.question_type, q.title, q.content,
                       q.answer, q.score, q.difficulty, q.options, q.explanation,
                       q.user_id, q.reg_date, q.status,
                       qc.category_nm
                FROM LM_QUESTION q
                LEFT JOIN LM_QUESTION_CATEGORY qc ON qc.id = q.category_id AND qc.site_id = :siteId AND qc.status != -1
                WHERE q.site_id = :siteId AND q.status != -1
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", siteId);

        // 왜: 교수자는 본인 등록 문제만 조회합니다.
        if (!isAdmin) {
            sql.append(" AND q.user_id = :userId ");
            params.addValue("userId", userId);
        }

        if (categoryId > 0) {
            sql.append(" AND q.category_id = :categoryId ");
            params.addValue("categoryId", categoryId);
        }
        if (!questionType.isEmpty()) {
            sql.append(" AND q.question_type = :questionType ");
            params.addValue("questionType", questionType);
        }
        if (!keyword.isEmpty()) {
            sql.append(" AND (q.title LIKE :keyword OR q.content LIKE :keyword) ");
            params.addValue("keyword", "%" + keyword + "%");
        }

        sql.append(" ORDER BY q.id DESC ");

        // 왜: 페이지네이션 적용
        int offset = (page - 1) * pageSize;
        sql.append(" LIMIT :pageSize OFFSET :offset ");
        params.addValue("pageSize", pageSize);
        params.addValue("offset", offset);

        return jdbc.queryForList(sql.toString(), params);
    }

    public long insertQuestion(long userId, long siteId, int categoryId, String questionType,
                               String title, String content, String answer, int score,
                               int difficulty, String options, String explanation) {
        String sql = """
                INSERT INTO LM_QUESTION (site_id, user_id, category_id, question_type,
                    title, content, answer, score, difficulty, options, explanation,
                    status, reg_date)
                VALUES (:siteId, :userId, :categoryId, :questionType,
                    :title, :content, :answer, :score, :difficulty, :options, :explanation,
                    1, NOW())
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("siteId", siteId)
                .addValue("userId", userId)
                .addValue("categoryId", categoryId)
                .addValue("questionType", questionType)
                .addValue("title", title)
                .addValue("content", content)
                .addValue("answer", answer)
                .addValue("score", score)
                .addValue("difficulty", difficulty)
                .addValue("options", options)
                .addValue("explanation", explanation), keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public int updateQuestion(int questionId, long siteId, int categoryId, String questionType,
                              String title, String content, String answer, int score,
                              int difficulty, String options, String explanation) {
        String sql = """
                UPDATE LM_QUESTION SET
                    category_id = :categoryId, question_type = :questionType,
                    title = :title, content = :content, answer = :answer,
                    score = :score, difficulty = :difficulty,
                    options = :options, explanation = :explanation,
                    mod_date = NOW()
                WHERE id = :questionId AND site_id = :siteId AND status != -1
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("questionId", questionId)
                .addValue("siteId", siteId)
                .addValue("categoryId", categoryId)
                .addValue("questionType", questionType)
                .addValue("title", title)
                .addValue("content", content)
                .addValue("answer", answer)
                .addValue("score", score)
                .addValue("difficulty", difficulty)
                .addValue("options", options)
                .addValue("explanation", explanation));
    }

    public int softDeleteQuestion(int questionId, long siteId) {
        String sql = """
                UPDATE LM_QUESTION SET status = -1, mod_date = NOW()
                WHERE id = :questionId AND site_id = :siteId AND status != -1
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("questionId", questionId)
                .addValue("siteId", siteId));
    }

    public boolean isQuestionOwner(int questionId, long userId, long siteId) {
        String sql = """
                SELECT COUNT(*) FROM LM_QUESTION
                WHERE id = :questionId AND user_id = :userId AND site_id = :siteId AND status != -1
                """;
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("questionId", questionId)
                .addValue("userId", userId)
                .addValue("siteId", siteId), Integer.class);
        return count != null && count > 0;
    }

    // ==================== 문제 카테고리 (LM_QUESTION_CATEGORY) ====================

    public List<Map<String, Object>> listCategories(long siteId, int parentId) {
        String sql = """
                SELECT id, category_nm, parent_id, depth, sort, status, reg_date,
                       (SELECT COUNT(*) FROM LM_QUESTION q
                        WHERE q.category_id = qc.id AND q.site_id = :siteId AND q.status != -1) AS question_cnt,
                       (SELECT COUNT(*) FROM LM_QUESTION_CATEGORY cc
                        WHERE cc.parent_id = qc.id AND cc.site_id = :siteId AND cc.status != -1) AS child_cnt
                FROM LM_QUESTION_CATEGORY qc
                WHERE qc.site_id = :siteId AND qc.status != -1 AND qc.parent_id = :parentId
                ORDER BY qc.sort ASC, qc.id ASC
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("siteId", siteId)
                .addValue("parentId", parentId));
    }

    public long insertCategory(long siteId, String categoryNm, int parentId, int depth, int sort) {
        String sql = """
                INSERT INTO LM_QUESTION_CATEGORY (site_id, category_nm, parent_id, depth, sort, status, reg_date)
                VALUES (:siteId, :categoryNm, :parentId, :depth, :sort, 1, NOW())
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("siteId", siteId)
                .addValue("categoryNm", categoryNm)
                .addValue("parentId", parentId)
                .addValue("depth", depth)
                .addValue("sort", sort), keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public int updateCategory(int categoryId, long siteId, String categoryNm, int sort) {
        String sql = """
                UPDATE LM_QUESTION_CATEGORY SET category_nm = :categoryNm, sort = :sort, mod_date = NOW()
                WHERE id = :categoryId AND site_id = :siteId AND status != -1
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("categoryId", categoryId)
                .addValue("siteId", siteId)
                .addValue("categoryNm", categoryNm)
                .addValue("sort", sort));
    }

    public int softDeleteCategory(int categoryId, long siteId) {
        String sql = """
                UPDATE LM_QUESTION_CATEGORY SET status = -1, mod_date = NOW()
                WHERE id = :categoryId AND site_id = :siteId AND status != -1
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("categoryId", categoryId)
                .addValue("siteId", siteId));
    }

    public int getCategoryDepth(int categoryId, long siteId) {
        String sql = """
                SELECT depth FROM LM_QUESTION_CATEGORY
                WHERE id = :categoryId AND site_id = :siteId AND status != -1
                """;
        try {
            Integer depth = jdbc.queryForObject(sql, new MapSqlParameterSource()
                    .addValue("categoryId", categoryId)
                    .addValue("siteId", siteId), Integer.class);
            return depth != null ? depth : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public int calcNextCategorySort(long siteId, int parentId) {
        String sql = """
                SELECT COUNT(*) FROM LM_QUESTION_CATEGORY
                WHERE site_id = :siteId AND parent_id = :parentId AND status != -1
                """;
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("siteId", siteId)
                .addValue("parentId", parentId), Integer.class);
        return (count != null ? count : 0) + 1;
    }

    public int countChildCategories(int categoryId, long siteId) {
        String sql = """
                SELECT COUNT(*) FROM LM_QUESTION_CATEGORY
                WHERE parent_id = :categoryId AND site_id = :siteId AND status != -1
                """;
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("categoryId", categoryId)
                .addValue("siteId", siteId), Integer.class);
        return count != null ? count : 0;
    }

    public int countQuestionsByCategory(int categoryId, long siteId) {
        String sql = """
                SELECT COUNT(*) FROM LM_QUESTION
                WHERE category_id = :categoryId AND site_id = :siteId AND status != -1
                """;
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("categoryId", categoryId)
                .addValue("siteId", siteId), Integer.class);
        return count != null ? count : 0;
    }
}
