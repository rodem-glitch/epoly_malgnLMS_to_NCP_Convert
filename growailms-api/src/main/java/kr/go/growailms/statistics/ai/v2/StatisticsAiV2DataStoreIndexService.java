package kr.go.growailms.statistics.ai.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.go.growailms.global.vector.service.VectorStoreService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class StatisticsAiV2DataStoreIndexService {
    // 왜: PRD v2의 Data Store Agent는 RAG 기반으로 "가능한 데이터/연산"을 찾아줘야 합니다.
    //     1) 카탈로그(JSON)를 문서로 쪼개서 벡터 DB(Qdrant)에 넣고
    //     2) 질의(prompt)와 가장 비슷한 문서를 찾아
    //     3) 오케스트레이터가 실행계획을 더 안정적으로 만들게 합니다.
    //
    // 주의: 임베딩 키/벡터DB가 없으면 인덱싱이 실패할 수 있으니,
    //       호출측에서 예외를 잡고(폴백) 동작하게 합니다.

    private static final String DOMAIN = "statistics_ai_v2";

    private final ObjectMapper objectMapper;
    private final VectorStoreService vectorStoreService;

    public StatisticsAiV2DataStoreIndexService(
            ObjectMapper objectMapper,
            VectorStoreService vectorStoreService
    ) {
        this.objectMapper = objectMapper;
        this.vectorStoreService = vectorStoreService;
    }

    public int reindex() {
        JsonNode root = readCatalogJson();

        int count = 0;
        count += indexDataSources(root.path("dataSources"));
        count += indexOperations(root.path("operations"));
        count += indexMappings(root.path("mappings"));
        return count;
    }

    private int indexDataSources(JsonNode node) {
        if (node == null || !node.isArray()) return 0;

        int count = 0;
        for (JsonNode n : node) {
            String id = text(n, "id");
            String name = text(n, "name");
            String description = text(n, "description");
            String provider = text(n, "provider");

            if (!StringUtils.hasText(id)) continue;

            String docId = "stats-ai-v2:datasource:" + id;
            String text = "데이터소스\n"
                    + "id=" + id + "\n"
                    + "name=" + safe(name) + "\n"
                    + "provider=" + safe(provider) + "\n"
                    + "description=" + safe(description) + "\n"
                    + "dimensions=" + safe(n.path("dimensions").toString()) + "\n"
                    + "metrics=" + safe(n.path("metrics").toString());

            vectorStoreService.upsertText(docId, text, baseMeta("datasource", id, name));
            count++;
        }
        return count;
    }

    private int indexOperations(JsonNode node) {
        if (node == null || !node.isArray()) return 0;

        int count = 0;
        for (JsonNode n : node) {
            String id = text(n, "id");
            String description = text(n, "description");
            if (!StringUtils.hasText(id)) continue;

            String docId = "stats-ai-v2:operation:" + id.toUpperCase(Locale.ROOT);
            String text = "연산(op)\n"
                    + "id=" + id + "\n"
                    + "description=" + safe(description) + "\n"
                    + "params=" + safe(n.path("params").toString());

            vectorStoreService.upsertText(docId, text, baseMeta("operation", id, id));
            count++;
        }
        return count;
    }

    private int indexMappings(JsonNode node) {
        if (node == null || !node.isObject()) return 0;

        // 왜: mapping은 '정답' 데이터라서, 질문에서 지역/카테고리를 추론할 때 도움이 됩니다.
        String docId = "stats-ai-v2:mappings";
        String text = "매핑/힌트\n" + node.toString();
        vectorStoreService.upsertText(docId, text, baseMeta("mappings", "mappings", "매핑"));
        return 1;
    }

    private Map<String, Object> baseMeta(String kind, String id, String name) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("domain", DOMAIN);
        meta.put("kind", kind);
        meta.put("id", id);
        if (StringUtils.hasText(name)) {
            meta.put("name", name);
        }
        return meta;
    }

    private JsonNode readCatalogJson() {
        try (InputStream in = new ClassPathResource("statistics/ai/catalog-v2.json").getInputStream()) {
            return objectMapper.readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("AI 통계 v2 카탈로그 파일을 읽지 못했습니다. classpath:statistics/ai/catalog-v2.json", e);
        }
    }

    private String text(JsonNode node, String key) {
        if (node == null) return null;
        String v = node.path(key).asText(null);
        return StringUtils.hasText(v) ? v.trim() : null;
    }

    private String safe(String v) {
        return v == null ? "" : v;
    }
}
