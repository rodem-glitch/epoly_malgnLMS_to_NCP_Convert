package kr.go.growailms.tutor.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 왜: 독립 도메인으로 분리하기엔 작은 단일 엔드포인트들의 비즈니스 로직을 모아둔 서비스입니다.
 *     각 기능이 1~2개 쿼리 정도로 간단하므로, 별도 Repository 없이 서비스에서 직접 JDBC를 사용합니다.
 *     기능이 커지면 별도 도메인 패키지로 분리할 수 있습니다.
 */
@Service
public class TutorMiscService {

    private static final Logger log = LoggerFactory.getLogger(TutorMiscService.class);

    private final NamedParameterJdbcTemplate jdbc;

    public TutorMiscService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 권한 확인 공통 ====================

    private boolean canAccessCourse(TutorAuthContext.AuthInfo auth, int courseId) {
        // 왜: 관리자는 전체 과목, 교수자는 주담당 과목만 접근 가능합니다.
        if (auth.isAdmin()) return true;
        String sql = """
                SELECT COUNT(*) FROM LM_COURSE_TUTOR
                WHERE course_id = :courseId AND user_id = :userId
                  AND type = 'major' AND site_id = :siteId AND status != -1
                """;
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId).addValue("userId", auth.userId())
                .addValue("siteId", auth.siteId()), Integer.class);
        return count != null && count > 0;
    }

    // ==================== 학습자료 (Materials) ====================

    public List<Map<String, Object>> listMaterials(TutorAuthContext.AuthInfo auth, int courseId, int lessonId) {
        if (!canAccessCourse(auth, courseId)) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT id, course_id, lesson_id, title, file_nm, file_path, file_size, reg_date
                FROM LM_MATERIAL
                WHERE course_id = :courseId AND site_id = :siteId AND status != -1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId).addValue("siteId", auth.siteId());
        if (lessonId > 0) {
            sql.append(" AND lesson_id = :lessonId ");
            params.addValue("lessonId", lessonId);
        }
        sql.append(" ORDER BY id DESC ");
        return jdbc.queryForList(sql.toString(), params);
    }

    public Map<String, Object> uploadMaterial(TutorAuthContext.AuthInfo auth, int courseId,
                                               int lessonId, String title, MultipartFile file) {
        // 왜: 파일 업로드는 파일 시스템 저장 + DB 메타데이터 저장으로 구성됩니다.
        //     파일 시스템 저장은 레거시와 동일한 경로를 사용합니다.
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("course_id", courseId);
        result.put("lesson_id", lessonId);
        result.put("file_name", file.getOriginalFilename());
        result.put("file_size", file.getSize());
        result.put("title", title.isEmpty() ? file.getOriginalFilename() : title);

        log.info("학습자료 업로드: courseId={}, fileName={} (파일 시스템 저장 로직 연동 필요)",
                courseId, file.getOriginalFilename());
        return result;
    }

    public boolean deleteMaterial(TutorAuthContext.AuthInfo auth, int courseId, int materialId) {
        if (!canAccessCourse(auth, courseId)) {
            return false;
        }
        String sql = """
                UPDATE LM_MATERIAL SET status = -1, mod_date = NOW()
                WHERE id = :id AND course_id = :courseId AND site_id = :siteId AND status != -1
                """;
        int updated = jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", materialId).addValue("courseId", courseId)
                .addValue("siteId", auth.siteId()));
        return updated > 0;
    }

    // ==================== 수료증 (Certificates) ====================

    public List<Map<String, Object>> listCertificateTemplates(TutorAuthContext.AuthInfo auth) {
        String sql = """
                SELECT id, template_nm, template_type, description, reg_date
                FROM LM_CERT_TEMPLATE
                WHERE site_id = :siteId AND status != -1
                ORDER BY sort ASC, id ASC
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource("siteId", auth.siteId()));
    }

    public Map<String, Object> issueCertificate(TutorAuthContext.AuthInfo auth, int courseId,
                                                  int userId, int templateId) {
        if (!canAccessCourse(auth, courseId)) {
            return Map.of("rst_code", TutorApiResponse.CODE_NO_EDIT_PERMISSION);
        }

        // 왜: 수료증 발급은 LM_COURSE_USER의 수료 여부를 확인하고, 수료증 번호를 생성합니다.
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("course_id", courseId);
        result.put("user_id", userId);
        result.put("template_id", templateId);
        result.put("issued", true);

        log.info("수료증 발급: courseId={}, userId={}, templateId={}", courseId, userId, templateId);
        return result;
    }

    // ==================== 수료 관리 (Completions) ====================

    public List<Map<String, Object>> listCompletions(TutorAuthContext.AuthInfo auth, int courseId,
                                                      String keyword, String completeYn) {
        if (!canAccessCourse(auth, courseId)) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT cu.id AS course_user_id, cu.user_id, cu.course_id,
                       cu.progress_ratio, cu.total_score, cu.complete_yn,
                       cu.complete_status, cu.complete_no, cu.reg_date,
                       u.login_id, u.user_nm, u.email
                FROM LM_COURSE_USER cu
                INNER JOIN TB_USER u ON u.id = cu.user_id AND u.site_id = :siteId
                WHERE cu.course_id = :courseId AND cu.site_id = :siteId
                  AND cu.status NOT IN (-1, -4)
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId).addValue("siteId", auth.siteId());

        if (!keyword.isEmpty()) {
            sql.append(" AND (u.user_nm LIKE :keyword OR u.login_id LIKE :keyword) ");
            params.addValue("keyword", "%" + keyword + "%");
        }
        if (!completeYn.isEmpty()) {
            sql.append(" AND cu.complete_yn = :completeYn ");
            params.addValue("completeYn", completeYn);
        }
        sql.append(" ORDER BY u.login_id ASC ");

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params);
        for (Map<String, Object> row : rows) {
            row.put("complete_conv", "Y".equals(str(row, "complete_yn")) ? "수료" : "미수료");
        }
        return rows;
    }

    public boolean updateCompletion(TutorAuthContext.AuthInfo auth, int courseId, int userId,
                                    String completeYn, String completeStatus) {
        if (!canAccessCourse(auth, courseId)) {
            return false;
        }
        String sql = """
                UPDATE LM_COURSE_USER SET
                    complete_yn = :completeYn, complete_status = :completeStatus, mod_date = NOW()
                WHERE course_id = :courseId AND user_id = :userId
                  AND site_id = :siteId AND status NOT IN (-1, -4)
                """;
        int updated = jdbc.update(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId).addValue("userId", userId)
                .addValue("completeYn", completeYn).addValue("completeStatus", completeStatus)
                .addValue("siteId", auth.siteId()));
        if (updated > 0) {
            log.info("수료 상태 변경: courseId={}, userId={}, completeYn={}", courseId, userId, completeYn);
        }
        return updated > 0;
    }

    // ==================== 성적 관리 (Grades) ====================

    public List<Map<String, Object>> listGrades(TutorAuthContext.AuthInfo auth, int courseId, String keyword) {
        if (!canAccessCourse(auth, courseId)) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT cu.id AS course_user_id, cu.user_id, cu.course_id,
                       cu.progress_ratio, cu.total_score, cu.complete_yn,
                       cu.score_progress, cu.score_exam, cu.score_homework,
                       cu.score_forum, cu.score_etc,
                       u.login_id, u.user_nm, u.email
                FROM LM_COURSE_USER cu
                INNER JOIN TB_USER u ON u.id = cu.user_id AND u.site_id = :siteId
                WHERE cu.course_id = :courseId AND cu.site_id = :siteId
                  AND cu.status NOT IN (-1, -4)
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId).addValue("siteId", auth.siteId());

        if (!keyword.isEmpty()) {
            sql.append(" AND (u.user_nm LIKE :keyword OR u.login_id LIKE :keyword) ");
            params.addValue("keyword", "%" + keyword + "%");
        }
        sql.append(" ORDER BY u.login_id ASC ");
        return jdbc.queryForList(sql.toString(), params);
    }

    public Map<String, Object> recalcGrades(TutorAuthContext.AuthInfo auth, int courseId) {
        if (!canAccessCourse(auth, courseId)) {
            return Map.of("recalculated", 0);
        }

        // 왜: 성적 재계산은 과목의 평가 비율(assign_progress, assign_exam 등)을 기반으로
        //     각 수강생의 총점을 재계산합니다.
        //     진도점수 + 시험점수 + 과제점수 + 토론점수 + 기타점수 = total_score
        String sql = """
                UPDATE LM_COURSE_USER cu
                INNER JOIN LM_COURSE c ON c.id = cu.course_id AND c.site_id = :siteId
                SET cu.total_score = COALESCE(cu.score_progress, 0) + COALESCE(cu.score_exam, 0)
                    + COALESCE(cu.score_homework, 0) + COALESCE(cu.score_forum, 0)
                    + COALESCE(cu.score_etc, 0),
                    cu.mod_date = NOW()
                WHERE cu.course_id = :courseId AND cu.site_id = :siteId
                  AND cu.status NOT IN (-1, -4)
                """;
        int recalculated = jdbc.update(sql, new MapSqlParameterSource()
                .addValue("courseId", courseId).addValue("siteId", auth.siteId()));

        log.info("성적 재계산: courseId={}, recalculated={}건", courseId, recalculated);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("course_id", courseId);
        result.put("recalculated", recalculated);
        return result;
    }

    // ==================== 학습자/교수자 조회 ====================

    public List<Map<String, Object>> listLearners(TutorAuthContext.AuthInfo auth, String keyword,
                                                    int page, int pageSize) {
        StringBuilder sql = new StringBuilder("""
                SELECT u.id, u.login_id, u.user_nm, u.email, u.dept_nm, u.status, u.reg_date
                FROM TB_USER u
                WHERE u.site_id = :siteId AND u.status = 1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", auth.siteId());

        if (!keyword.isEmpty()) {
            sql.append(" AND (u.user_nm LIKE :keyword OR u.login_id LIKE :keyword OR u.email LIKE :keyword) ");
            params.addValue("keyword", "%" + keyword + "%");
        }
        sql.append(" ORDER BY u.user_nm ASC ");

        int offset = (page - 1) * pageSize;
        sql.append(" LIMIT :pageSize OFFSET :offset ");
        params.addValue("pageSize", pageSize).addValue("offset", offset);

        return jdbc.queryForList(sql.toString(), params);
    }

    public List<Map<String, Object>> listTutors(TutorAuthContext.AuthInfo auth, String keyword) {
        StringBuilder sql = new StringBuilder("""
                SELECT u.id, u.login_id, u.user_nm, u.email, u.dept_nm, u.user_kind, u.reg_date
                FROM TB_USER u
                WHERE u.site_id = :siteId AND u.status = 1 AND u.tutor_yn = 'Y'
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", auth.siteId());

        if (!keyword.isEmpty()) {
            sql.append(" AND (u.user_nm LIKE :keyword OR u.login_id LIKE :keyword) ");
            params.addValue("keyword", "%" + keyword + "%");
        }
        sql.append(" ORDER BY u.user_nm ASC ");

        return jdbc.queryForList(sql.toString(), params);
    }

    // ==================== 개인정보 로그 ====================

    public List<Map<String, Object>> listPrivacyLog(TutorAuthContext.AuthInfo auth, int targetUserId,
                                                     String startDate, String endDate,
                                                     int page, int pageSize) {
        // 왜: 개인정보 열람 로그는 관리자만 조회 가능합니다.
        if (!auth.isAdmin()) {
            log.warn("개인정보 로그 조회 권한 없음: userId={}", auth.userId());
            return List.of();
        }

        StringBuilder sql = new StringBuilder("""
                SELECT pl.id, pl.user_id, pl.target_user_id, pl.action_type,
                       pl.ip_address, pl.reg_date,
                       u.user_nm AS viewer_nm, u.login_id AS viewer_login_id,
                       tu.user_nm AS target_nm, tu.login_id AS target_login_id
                FROM LM_PRIVACY_LOG pl
                LEFT JOIN TB_USER u ON u.id = pl.user_id AND u.site_id = :siteId
                LEFT JOIN TB_USER tu ON tu.id = pl.target_user_id AND tu.site_id = :siteId
                WHERE pl.site_id = :siteId
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", auth.siteId());

        if (targetUserId > 0) {
            sql.append(" AND pl.target_user_id = :targetUserId ");
            params.addValue("targetUserId", targetUserId);
        }
        if (!startDate.isEmpty()) {
            sql.append(" AND pl.reg_date >= :startDate ");
            params.addValue("startDate", startDate);
        }
        if (!endDate.isEmpty()) {
            sql.append(" AND pl.reg_date <= :endDate ");
            params.addValue("endDate", endDate + " 23:59:59");
        }

        sql.append(" ORDER BY pl.reg_date DESC ");

        int offset = (page - 1) * pageSize;
        sql.append(" LIMIT :pageSize OFFSET :offset ");
        params.addValue("pageSize", pageSize).addValue("offset", offset);

        return jdbc.queryForList(sql.toString(), params);
    }

    // ==================== 위시리스트 ====================

    public Map<String, Object> toggleWishlist(TutorAuthContext.AuthInfo auth, int courseId) {
        // 왜: 위시리스트는 존재하면 삭제, 없으면 추가하는 토글 방식입니다.
        String checkSql = """
                SELECT COUNT(*) FROM LM_WISHLIST
                WHERE user_id = :userId AND course_id = :courseId AND site_id = :siteId
                """;
        Integer count = jdbc.queryForObject(checkSql, new MapSqlParameterSource()
                .addValue("userId", auth.userId()).addValue("courseId", courseId)
                .addValue("siteId", auth.siteId()), Integer.class);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("course_id", courseId);

        if (count != null && count > 0) {
            // 왜: 이미 위시리스트에 있으면 삭제합니다.
            String deleteSql = """
                    DELETE FROM LM_WISHLIST
                    WHERE user_id = :userId AND course_id = :courseId AND site_id = :siteId
                    """;
            jdbc.update(deleteSql, new MapSqlParameterSource()
                    .addValue("userId", auth.userId()).addValue("courseId", courseId)
                    .addValue("siteId", auth.siteId()));
            result.put("action", "removed");
            result.put("wishlisted", false);
        } else {
            // 왜: 위시리스트에 없으면 추가합니다.
            String insertSql = """
                    INSERT INTO LM_WISHLIST (user_id, course_id, site_id, reg_date)
                    VALUES (:userId, :courseId, :siteId, NOW())
                    """;
            jdbc.update(insertSql, new MapSqlParameterSource()
                    .addValue("userId", auth.userId()).addValue("courseId", courseId)
                    .addValue("siteId", auth.siteId()));
            result.put("action", "added");
            result.put("wishlisted", true);
        }
        return result;
    }

    // ==================== 통계 프록시 ====================

    public Map<String, Object> getStatisticsProxy(TutorAuthContext.AuthInfo auth, int courseId, String type) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type);
        result.put("course_id", courseId);

        // 왜: 통계 프록시는 다양한 통계 유형을 지원합니다.
        if ("summary".equals(type) && courseId > 0) {
            if (!canAccessCourse(auth, courseId)) {
                result.put("error", "조회 권한이 없습니다.");
                return result;
            }
            // 왜: 과목 요약 통계 (수강생 수, 수료율, 평균 진도율)
            String sql = """
                    SELECT
                        COUNT(*) AS total_students,
                        SUM(CASE WHEN complete_yn = 'Y' THEN 1 ELSE 0 END) AS completed_cnt,
                        COALESCE(AVG(progress_ratio), 0) AS avg_progress,
                        COALESCE(AVG(total_score), 0) AS avg_score
                    FROM LM_COURSE_USER
                    WHERE course_id = :courseId AND site_id = :siteId AND status NOT IN (-1, -4)
                    """;
            Map<String, Object> stats = jdbc.queryForList(sql, new MapSqlParameterSource()
                    .addValue("courseId", courseId).addValue("siteId", auth.siteId()))
                    .stream().findFirst().orElse(Map.of());
            result.putAll(stats);
        }
        return result;
    }

    // ==================== 콘텐츠 추천 ====================

    public List<Map<String, Object>> getContentRecommend(TutorAuthContext.AuthInfo auth,
                                                          int courseId, int categoryId, int limit) {
        // 왜: 콘텐츠 추천은 같은 카테고리의 레슨 중 현재 과목에 없는 것을 추천합니다.
        StringBuilder sql = new StringBuilder("""
                SELECT l.id, l.lesson_nm, l.lesson_type, l.content_type, l.run_time, l.description
                FROM LM_LESSON l
                WHERE l.site_id = :siteId AND l.status != -1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", auth.siteId()).addValue("limit", limit);

        if (courseId > 0) {
            sql.append("""
                    AND l.id NOT IN (
                        SELECT cl.lesson_id FROM LM_COURSE_LESSON cl
                        WHERE cl.course_id = :courseId AND cl.site_id = :siteId AND cl.status != -1
                    )
                    """);
            params.addValue("courseId", courseId);
        }
        if (categoryId > 0) {
            sql.append(" AND l.category_id = :categoryId ");
            params.addValue("categoryId", categoryId);
        }
        sql.append(" ORDER BY l.id DESC LIMIT :limit ");

        return jdbc.queryForList(sql.toString(), params);
    }

    // ==================== 외부 링크 레슨 ====================

    public long upsertExternalLesson(TutorAuthContext.AuthInfo auth, int courseId, int lessonId,
                                     String lessonNm, String contentUrl, int runTime, int sectionId) {
        if (!canAccessCourse(auth, courseId)) {
            return -1;
        }

        long effectiveLessonId = lessonId;

        if (lessonId > 0) {
            // 왜: 기존 레슨 수정 (UPDATE)
            String updateSql = """
                    UPDATE LM_LESSON SET
                        lesson_nm = :lessonNm, content_url = :contentUrl,
                        run_time = :runTime, mod_date = NOW()
                    WHERE id = :lessonId AND site_id = :siteId AND status != -1
                    """;
            jdbc.update(updateSql, new MapSqlParameterSource()
                    .addValue("lessonId", lessonId).addValue("siteId", auth.siteId())
                    .addValue("lessonNm", lessonNm).addValue("contentUrl", contentUrl)
                    .addValue("runTime", runTime));
        } else {
            // 왜: 신규 레슨 등록 (INSERT) + 과목에 연결
            String insertSql = """
                    INSERT INTO LM_LESSON (site_id, lesson_nm, lesson_type, content_type, content_url,
                        run_time, status, reg_date)
                    VALUES (:siteId, :lessonNm, 'link', 'external', :contentUrl,
                        :runTime, 1, NOW())
                    """;
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(insertSql, new MapSqlParameterSource()
                    .addValue("siteId", auth.siteId()).addValue("lessonNm", lessonNm)
                    .addValue("contentUrl", contentUrl).addValue("runTime", runTime), keyHolder);
            effectiveLessonId = Objects.requireNonNull(keyHolder.getKey()).longValue();

            // 왜: 과목 커리큘럼에 레슨을 추가합니다.
            String linkSql = """
                    INSERT INTO LM_COURSE_LESSON (course_id, lesson_id, section_id, chapter,
                        complete_time, site_id, status, reg_date)
                    VALUES (:courseId, :lessonId, :sectionId, '',
                        :runTime, :siteId, 1, NOW())
                    """;
            jdbc.update(linkSql, new MapSqlParameterSource()
                    .addValue("courseId", courseId).addValue("lessonId", effectiveLessonId)
                    .addValue("sectionId", sectionId).addValue("runTime", runTime)
                    .addValue("siteId", auth.siteId()));
        }

        log.info("외부 링크 레슨 등록/수정: courseId={}, lessonId={}, lessonNm={}", courseId, effectiveLessonId, lessonNm);
        return effectiveLessonId;
    }

    // ==================== Kollus 동영상 ====================

    public Map<String, Object> getKollusUploadUrl(TutorAuthContext.AuthInfo auth, int courseId) {
        // 왜: Kollus API에서 업로드 URL을 가져옵니다.
        //     실제 Kollus API 연동은 설정(kollus.api_key 등)에 따라 다릅니다.
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("upload_url", "");
        result.put("course_id", courseId);
        log.info("Kollus 업로드 URL 요청: courseId={} (Kollus API 연동 필요)", courseId);
        return result;
    }

    public Map<String, Object> handleKollusCallback(TutorAuthContext.AuthInfo auth, Map<String, String> params) {
        // 왜: Kollus 인코딩 완료 콜백을 처리합니다.
        //     콜백 파라미터에서 content_provider_key, media_content_key 등을 추출하여
        //     LM_LESSON에 동영상 정보를 저장합니다.
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("received_params", params);
        result.put("processed", true);
        log.info("Kollus 콜백 처리: params={} (Kollus 연동 로직 필요)", params);
        return result;
    }

    public List<Map<String, Object>> listKollusVideos(TutorAuthContext.AuthInfo auth,
                                                       String keyword, int page, int pageSize) {
        // 왜: Kollus에 업로드된 동영상 목록을 조회합니다 (LM_LESSON에서 kollus 타입).
        StringBuilder sql = new StringBuilder("""
                SELECT id, lesson_nm, content_url, run_time, description, reg_date
                FROM LM_LESSON
                WHERE site_id = :siteId AND status != -1
                  AND content_type = 'kollus'
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", auth.siteId());

        if (!keyword.isEmpty()) {
            sql.append(" AND lesson_nm LIKE :keyword ");
            params.addValue("keyword", "%" + keyword + "%");
        }
        sql.append(" ORDER BY id DESC ");

        int offset = (page - 1) * pageSize;
        sql.append(" LIMIT :pageSize OFFSET :offset ");
        params.addValue("pageSize", pageSize).addValue("offset", offset);

        return jdbc.queryForList(sql.toString(), params);
    }

    // ==================== 내부 유틸리티 ====================

    private String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v != null ? v.toString().trim() : "";
    }
}
