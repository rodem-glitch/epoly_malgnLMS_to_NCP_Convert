package kr.go.growailms.global.vector.service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import kr.go.growailms.global.vector.config.VectorSearchConfig;
import kr.go.growailms.global.vector.service.dto.VectorSearchResult;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class VectorStoreService {

    private final VectorStore vectorStore;
    private final VectorSearchConfig vectorSearchConfig;

    public VectorStoreService(VectorStore vectorStore, VectorSearchConfig vectorSearchConfig) {
        // 왜: Qdrant/임베딩 구현체에 묶이지 않고, Spring AI의 VectorStore 추상화만 의존해 재사용성을 높입니다.
        this.vectorStore = Objects.requireNonNull(vectorStore);
        // 왜: topK 상한/튜닝값을 코드가 아니라 설정으로 조정할 수 있어야 운영에서 실험이 쉽습니다.
        this.vectorSearchConfig = Objects.requireNonNull(vectorSearchConfig);
    }

    public void upsertText(String text, Map<String, Object> metadata) {
        // 왜: 기존 코드 호환을 위해 id 없이 적재하는 메서드를 남깁니다.
        if (text == null || text.isBlank()) return;
        vectorStore.add(List.of(new Document(text, safeMetadata(metadata))));
    }

    public void upsertText(String id, String text, Map<String, Object> metadata) {
        // 왜: 같은 lesson_id를 여러 번 적재해도 "덮어쓰기(upsert)"가 되도록 문서 id를 고정합니다.
        if (text == null || text.isBlank()) return;

        if (id == null || id.isBlank()) {
            vectorStore.add(List.of(new Document(text, safeMetadata(metadata))));
            return;
        }

        String stableId = toStableDocumentId(id);
        Map<String, Object> safeMetadata = safeMetadata(metadata);

        if (!stableId.equals(id)) {
            // 왜: Qdrant는 UUID를 문서 id로 요구하는 경우가 있어, 원본 id는 메타데이터로 보존합니다.
            safeMetadata.putIfAbsent("doc_id", id);
        }

        vectorStore.add(List.of(new Document(stableId, text, safeMetadata)));
    }

    private Map<String, Object> safeMetadata(Map<String, Object> metadata) {
        // 왜: 호출자가 넘긴 Map을 그대로 쓰면, 이후 수정/재사용 시 예기치 않은 사이드이펙트가 날 수 있습니다.
        if (metadata == null) return new HashMap<>();
        return new HashMap<>(metadata);
    }

    private String toStableDocumentId(String id) {
        // 왜: Qdrant(VectorStore 구현체)는 문서 id를 UUID로 파싱하는 경우가 있어, 임의 문자열 id를 그대로 쓰면 실패합니다.
        try {
            UUID.fromString(id);
            return id;
        } catch (IllegalArgumentException ignored) {
            return UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)).toString();
        }
    }

    public List<VectorSearchResult> similaritySearch(String query, int topK, double similarityThreshold) {
        return similaritySearch(query, topK, similarityThreshold, null);
    }

    public List<VectorSearchResult> similaritySearch(
        String query,
        int topK,
        double similarityThreshold,
        String filterExpression
    ) {
        if (query == null || query.isBlank()) return List.of();

        int maxTopK = vectorSearchConfig.maxTopKOrDefault();
        int safeTopK = Math.max(1, Math.min(topK, maxTopK));
        double safeThreshold = Math.max(0.0, Math.min(similarityThreshold, 1.0));

        SearchRequest.Builder builder = SearchRequest.builder()
            .query(query)
            .topK(safeTopK)
            .similarityThreshold(safeThreshold);

        if (filterExpression != null && !filterExpression.isBlank()) {
            // 왜: 교수자/학생 추천처럼 "상황"이 있는 검색은 메타데이터 필터를 걸어야 품질이 안정적으로 올라갑니다.
            builder.filterExpression(filterExpression);
        }

        List<Document> results = vectorStore.similaritySearch(builder.build());

        return results.stream()
            .map(d -> new VectorSearchResult(d.getText(), d.getMetadata(), d.getScore()))
            .toList();
    }
}
