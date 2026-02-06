package kr.go.growailms.global.vector.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.go.growailms.global.vector.service.dto.IndexLessonsRequest;
import kr.go.growailms.global.vector.service.dto.IndexLessonsResponse;
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
        sql.append("""
            SELECT
                ID AS lesson_id,
                SITE_ID AS site_id,
                CONTENT_ID AS content_id,
                LESSON_TYPE AS lesson_type,
                LESSON_NM AS lesson_nm,
                DESCRIPTION AS summary_text,
                c.CATEGORY_ID AS category_id,
                c.CONTENT_NM AS content_nm
            FROM LM_LESSON
            LEFT JOIN LM_CONTENT c
              ON c.ID = LM_LESSON.CONTENT_ID
             AND c.SITE_ID = LM_LESSON.SITE_ID
             AND c.STATUS = 1
            WHERE STATUS = 1
              AND USE_YN = 'Y'
              AND DESCRIPTION IS NOT NULL
              AND DESCRIPTION <> ''
            """);

        Map<String, Object> params = new HashMap<>();
        if (request.siteId() != null) {
            sql.append(" AND SITE_ID = ? ");
            params.put("site_id", request.siteId());
        }
        if (request.lessonType() != null && !request.lessonType().isBlank()) {
            sql.append(" AND LESSON_TYPE = ? ");
            params.put("lesson_type", request.lessonType());
        }

        sql.append(" ORDER BY ID ASC ");
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
