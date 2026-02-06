package kr.go.growailms.global.vector.service;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import kr.go.growailms.global.vector.config.VectorSearchConfig;
import kr.go.growailms.global.vector.service.dto.VectorSearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * RETRIEVAL_QUERY task-type으로 쿼리 임베딩을 생성하고 Qdrant에 직접 검색하는 서비스.
 * 왜: Spring AI VectorStore.similaritySearch()는 기본 task-type(RETRIEVAL_DOCUMENT)을 사용하므로,
 *     쿼리용 task-type(RETRIEVAL_QUERY)을 적용하려면 직접 Qdrant 클라이언트를 사용해야 합니다.
 */
@Service
public class VectorQueryService {

    private final QueryEmbeddingService queryEmbeddingService;
    private final QdrantClient qdrantClient;
    private final VectorSearchConfig vectorSearchConfig;
    private final String collectionName;

    public VectorQueryService(
        QueryEmbeddingService queryEmbeddingService,
        QdrantClient qdrantClient,
        VectorSearchConfig vectorSearchConfig,
        @Value("${spring.ai.vectorstore.qdrant.collection-name:video_summary_vectors}") String collectionName
    ) {
        this.queryEmbeddingService = Objects.requireNonNull(queryEmbeddingService);
        this.qdrantClient = Objects.requireNonNull(qdrantClient);
        this.vectorSearchConfig = Objects.requireNonNull(vectorSearchConfig);
        this.collectionName = collectionName;
    }

    /**
     * RETRIEVAL_QUERY task-type으로 쿼리를 임베딩하고 Qdrant에서 유사 문서를 검색합니다.
     */
    public List<VectorSearchResult> similaritySearchWithQueryTaskType(
        String query,
        int topK,
        double similarityThreshold,
        String filterExpression
    ) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        // 1. RETRIEVAL_QUERY task-type으로 쿼리 임베딩 생성
        float[] queryVector = queryEmbeddingService.embedQuery(query);
        if (queryVector == null || queryVector.length == 0) {
            return List.of();
        }

        // 2. topK 제한 적용
        int maxTopK = vectorSearchConfig.maxTopKOrDefault();
        int safeTopK = Math.max(1, Math.min(topK, maxTopK));
        double safeThreshold = Math.max(0.0, Math.min(similarityThreshold, 1.0));

        // 3. Qdrant에 직접 검색
        try {
            SearchPoints.Builder searchBuilder = SearchPoints.newBuilder()
                .setCollectionName(collectionName)
                .addAllVector(toFloatList(queryVector))
                .setLimit(safeTopK)
                .setScoreThreshold((float) safeThreshold)
                .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build());

            // 필터 적용 (간단한 source 필터만 지원)
            if (filterExpression != null && filterExpression.contains("source == 'tb_reco_content'")) {
                searchBuilder.setFilter(Points.Filter.newBuilder()
                    .addMust(Points.Condition.newBuilder()
                        .setField(Points.FieldCondition.newBuilder()
                            .setKey("source")
                            .setMatch(Points.Match.newBuilder().setKeyword("tb_reco_content").build())
                            .build())
                        .build())
                    .build());
            }

            List<ScoredPoint> results = qdrantClient.searchAsync(searchBuilder.build()).get();
            return convertToVectorSearchResults(results);

        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    private List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float f : array) {
            list.add(f);
        }
        return list;
    }

    private List<VectorSearchResult> convertToVectorSearchResults(List<ScoredPoint> scoredPoints) {
        List<VectorSearchResult> results = new ArrayList<>();
        for (ScoredPoint point : scoredPoints) {
            Map<String, Object> metadata = new HashMap<>();
            StringBuilder textBuilder = new StringBuilder();

            // payload에서 메타데이터 추출
            point.getPayloadMap().forEach((key, value) -> {
                if ("text".equals(key) || "content".equals(key)) {
                    textBuilder.append(value.getStringValue());
                } else {
                    // 값 타입에 따라 변환
                    switch (value.getKindCase()) {
                        case STRING_VALUE -> metadata.put(key, value.getStringValue());
                        case INTEGER_VALUE -> metadata.put(key, value.getIntegerValue());
                        case DOUBLE_VALUE -> metadata.put(key, value.getDoubleValue());
                        case BOOL_VALUE -> metadata.put(key, value.getBoolValue());
                        default -> metadata.put(key, value.toString());
                    }
                }
            });

            results.add(new VectorSearchResult(textBuilder.toString(), metadata, point.getScore()));
        }
        return results;
    }
}
