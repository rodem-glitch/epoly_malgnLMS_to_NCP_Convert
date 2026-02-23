package kr.polytech.lms.global.vector.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.polytech.lms.global.vector.service.dto.IndexLessonsRequest;
import kr.polytech.lms.global.vector.service.dto.IndexLessonsResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class VectorIndexService {

    private final JdbcTemplate jdbcTemplate;
    private final VectorStoreService vectorStoreService;

    public VectorIndexService(JdbcTemplate jdbcTemplate, VectorStoreService vectorStoreService) {
        // 왜: 레거시 DB를 그대로 쓰는 구조라, JPA 엔티티를 강제하지 않고 JdbcTemplate로 빠르게 인덱싱합니다.
        this.jdbcTemplate = jdbcTemplate;
        this.vectorStoreService = vectorStoreService;
    }

    public IndexLessonsResponse indexLessonsFromLegacy(IndexLessonsRequest request) {
        int limit = request.limitOrDefault();
        int offset = request.offsetOrDefault();

        StringBuilder sql = new StringBuilder();
        // 왜: LEFT JOIN 시 ID, SITE_ID 등이 양쪽 테이블에 존재하여 'Column ambiguous' 오류가 발생하므로
        //     SELECT/WHERE/ORDER BY 모든 컬럼에 LM_LESSON. 접두어를 명시합니다.
        sql.append("""
            SELECT
                LM_LESSON.ID AS lesson_id,
                LM_LESSON.SITE_ID AS site_id,
                LM_LESSON.CONTENT_ID AS content_id,
                LM_LESSON.LESSON_TYPE AS lesson_type,
                LM_LESSON.LESSON_NM AS lesson_nm,
                LM_LESSON.DESCRIPTION AS summary_text,
                c.CATEGORY_ID AS category_id,
                c.CONTENT_NM AS content_nm
            FROM LM_LESSON
            LEFT JOIN LM_CONTENT c
              ON c.ID = LM_LESSON.CONTENT_ID
             AND c.SITE_ID = LM_LESSON.SITE_ID
             AND c.STATUS = 1
            WHERE LM_LESSON.STATUS = 1
              AND LM_LESSON.USE_YN = 'Y'
              AND LM_LESSON.DESCRIPTION IS NOT NULL
              AND LM_LESSON.DESCRIPTION <> ''
            """);

        Map<String, Object> params = new HashMap<>();
        if (request.siteId() != null) {
            sql.append(" AND LM_LESSON.SITE_ID = ? ");
            params.put("site_id", request.siteId());
        }
        if (request.lessonType() != null && !request.lessonType().isBlank()) {
            sql.append(" AND LM_LESSON.LESSON_TYPE = ? ");
            params.put("lesson_type", request.lessonType());
        }

        sql.append(" ORDER BY LM_LESSON.ID ASC ");
        sql.append(" LIMIT ? OFFSET ? ");

        List<Object> args = new java.util.ArrayList<>();
        if (params.containsKey("site_id")) args.add(params.get("site_id"));
        if (params.containsKey("lesson_type")) args.add(params.get("lesson_type"));
        args.add(limit);
        args.add(offset);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());

        for (Map<String, Object> row : rows) {
            String lessonId = String.valueOf(row.get("lesson_id"));
            String siteId = String.valueOf(row.get("site_id"));
            String docId = "lesson:" + siteId + ":" + lessonId;

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("lesson_id", row.get("lesson_id"));
            metadata.put("site_id", row.get("site_id"));
            metadata.put("content_id", row.get("content_id"));
            metadata.put("lesson_type", row.get("lesson_type"));
            metadata.put("lesson_nm", row.get("lesson_nm"));
            metadata.put("category_id", row.get("category_id"));
            metadata.put("content_nm", row.get("content_nm"));

            String text = String.valueOf(row.get("summary_text"));
            vectorStoreService.upsertText(docId, text, metadata);
        }

        return new IndexLessonsResponse(rows.size(), rows.size());
    }
}
