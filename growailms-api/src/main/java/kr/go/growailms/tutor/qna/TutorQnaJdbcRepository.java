package kr.go.growailms.tutor.qna;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 왜: 레거시 DAO(PostDao, BoardDao)가 하던 DB 접근을
 *     Spring Boot의 NamedParameterJdbcTemplate으로 대체합니다.
 *     테이블: CL_POST, CL_BOARD, TB_USER
 */
@Repository
public class TutorQnaJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(TutorQnaJdbcRepository.class);
    private final NamedParameterJdbcTemplate jdbc;

    public TutorQnaJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 권한 확인 ====================

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

    // ==================== qna_list.jsp ====================

    /**
     * 왜: 특정 과목의 QnA 게시판 글 목록을 조회합니다.
     *     CL_BOARD에서 course_id + board_type='qna'인 게시판을 찾고,
     *     CL_POST에서 해당 게시판의 글을 조회합니다.
     */
    public List<Map<String, Object>> listQna(int courseId, long siteId, String keyword,
                                              String answerStatus, int page, int pageSize) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.board_id, p.title, p.content, p.user_id,
                       p.answer_content, p.answer_user_id, p.answer_date,
                       p.hit_cnt, p.reg_date, p.status,
                       u.user_nm, u.login_id,
                       b.course_id
                FROM CL_POST p
                INNER JOIN CL_BOARD b ON b.id = p.board_id AND b.site_id = :siteId AND b.status != -1
                LEFT JOIN TB_USER u ON u.id = p.user_id AND u.site_id = :siteId
                WHERE b.course_id = :courseId
                  AND b.board_type = 'qna'
                  AND p.site_id = :siteId
                  AND p.status != -1
                  AND (p.parent_id IS NULL OR p.parent_id = 0)
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("siteId", siteId);

        if (!keyword.isEmpty()) {
            sql.append(" AND (p.title LIKE :keyword OR p.content LIKE :keyword) ");
            params.addValue("keyword", "%" + keyword + "%");
        }

        // 왜: 답변 상태 필터링 (answered: 답변완료, unanswered: 미답변)
        if ("answered".equals(answerStatus)) {
            sql.append(" AND p.answer_content IS NOT NULL AND p.answer_content != '' ");
        } else if ("unanswered".equals(answerStatus)) {
            sql.append(" AND (p.answer_content IS NULL OR p.answer_content = '') ");
        }

        sql.append(" ORDER BY p.reg_date DESC ");

        // 왜: 페이지네이션 적용
        int offset = (page - 1) * pageSize;
        sql.append(" LIMIT :pageSize OFFSET :offset ");
        params.addValue("pageSize", pageSize);
        params.addValue("offset", offset);

        return jdbc.queryForList(sql.toString(), params);
    }

    // ==================== qna_view.jsp ====================

    public Optional<Map<String, Object>> getQnaPost(int postId, long siteId) {
        String sql = """
                SELECT p.id, p.board_id, p.title, p.content, p.user_id,
                       p.answer_content, p.answer_user_id, p.answer_date,
                       p.hit_cnt, p.reg_date, p.mod_date, p.status,
                       p.parent_id,
                       u.user_nm, u.login_id, u.email,
                       b.course_id, b.board_nm
                FROM CL_POST p
                INNER JOIN CL_BOARD b ON b.id = p.board_id AND b.site_id = :siteId
                LEFT JOIN TB_USER u ON u.id = p.user_id AND u.site_id = :siteId
                WHERE p.id = :postId AND p.site_id = :siteId AND p.status != -1
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("postId", postId)
                .addValue("siteId", siteId)).stream().findFirst();
    }

    public List<Map<String, Object>> listReplies(int parentPostId, long siteId) {
        String sql = """
                SELECT p.id, p.title, p.content, p.user_id, p.reg_date, p.status,
                       u.user_nm, u.login_id
                FROM CL_POST p
                LEFT JOIN TB_USER u ON u.id = p.user_id AND u.site_id = :siteId
                WHERE p.parent_id = :parentId AND p.site_id = :siteId AND p.status != -1
                ORDER BY p.reg_date ASC
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("parentId", parentPostId)
                .addValue("siteId", siteId));
    }

    // ==================== qna_answer.jsp ====================

    /**
     * 왜: QnA 답변은 원글의 answer_content/answer_user_id/answer_date 필드를 업데이트합니다.
     *     레거시 JSP에서 PostDao.updateAnswer()로 하던 것을 재현합니다.
     */
    public int updateAnswer(int postId, long answerUserId, String content, long siteId) {
        String sql = """
                UPDATE CL_POST SET
                    answer_content = :content,
                    answer_user_id = :answerUserId,
                    answer_date = NOW(),
                    mod_date = NOW()
                WHERE id = :postId AND site_id = :siteId AND status != -1
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("postId", postId)
                .addValue("content", content)
                .addValue("answerUserId", answerUserId)
                .addValue("siteId", siteId));
    }

    // ==================== qna_manage_list.jsp ====================

    /**
     * 왜: 교수자의 전체 담당 과목에 걸친 QnA를 한 번에 조회합니다.
     *     qna_manage_list.jsp에서 CourseTutorDao로 과목 목록을 먼저 조회하고
     *     각 과목의 QnA를 개별 조회하던 것을, 한 번의 JOIN 쿼리로 통합합니다.
     */
    public List<Map<String, Object>> manageListQna(long userId, long siteId, boolean isAdmin,
                                                    String keyword, String answerStatus,
                                                    int courseId, int page, int pageSize) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.board_id, p.title, p.content, p.user_id,
                       p.answer_content, p.answer_user_id, p.answer_date,
                       p.hit_cnt, p.reg_date, p.status,
                       u.user_nm, u.login_id,
                       b.course_id,
                       c.course_nm
                FROM CL_POST p
                INNER JOIN CL_BOARD b ON b.id = p.board_id AND b.site_id = :siteId AND b.status != -1
                INNER JOIN LM_COURSE c ON c.id = b.course_id AND c.site_id = :siteId AND c.status != -1
                LEFT JOIN TB_USER u ON u.id = p.user_id AND u.site_id = :siteId
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", siteId);

        // 왜: 교수자는 자기 담당 과목의 QnA만 봅니다.
        if (!isAdmin) {
            sql.append("""
                    INNER JOIN LM_COURSE_TUTOR ct ON ct.course_id = c.id
                        AND ct.user_id = :userId AND ct.type = 'major'
                        AND ct.site_id = :siteId AND ct.status != -1
                    """);
            params.addValue("userId", userId);
        }

        sql.append("""
                WHERE b.board_type = 'qna'
                  AND p.site_id = :siteId
                  AND p.status != -1
                  AND (p.parent_id IS NULL OR p.parent_id = 0)
                """);

        if (courseId > 0) {
            sql.append(" AND b.course_id = :courseId ");
            params.addValue("courseId", courseId);
        }
        if (!keyword.isEmpty()) {
            sql.append(" AND (p.title LIKE :keyword OR p.content LIKE :keyword OR c.course_nm LIKE :keyword) ");
            params.addValue("keyword", "%" + keyword + "%");
        }
        if ("answered".equals(answerStatus)) {
            sql.append(" AND p.answer_content IS NOT NULL AND p.answer_content != '' ");
        } else if ("unanswered".equals(answerStatus)) {
            sql.append(" AND (p.answer_content IS NULL OR p.answer_content = '') ");
        }

        sql.append(" ORDER BY p.reg_date DESC ");

        int offset = (page - 1) * pageSize;
        sql.append(" LIMIT :pageSize OFFSET :offset ");
        params.addValue("pageSize", pageSize);
        params.addValue("offset", offset);

        return jdbc.queryForList(sql.toString(), params);
    }
}
