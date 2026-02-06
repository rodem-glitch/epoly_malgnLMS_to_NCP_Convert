package kr.go.growailms.global.vector.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 쿼리용 임베딩을 RETRIEVAL_QUERY task-type으로 생성하는 서비스.
 * 왜: Spring AI EmbeddingModel은 EmbeddingRequest의 옵션을 무시하는 버그가 있어,
 *     Google REST API를 직접 호출하여 task-type을 확실하게 적용합니다.
 */
@Service
public class QueryEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(QueryEmbeddingService.class);

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String modelName;

    public QueryEmbeddingService(
        @Value("${spring.ai.google.genai.embedding.api-key:${GOOGLE_API_KEY:}}") String apiKey,
        @Value("${spring.ai.google.genai.embedding.text.options.model:gemini-embedding-001}") String modelName
    ) {
        this.restTemplate = new RestTemplate();
        this.apiKey = apiKey;
        this.modelName = modelName;
        log.info("[QueryEmbeddingService] 초기화 완료. 모델: {}, API키 설정: {}", 
            modelName, (apiKey != null && !apiKey.isBlank()) ? "✅" : "❌");
    }

    /**
     * RETRIEVAL_QUERY task-type으로 쿼리 텍스트를 임베딩합니다.
     * Google REST API를 직접 호출하여 task-type을 확실하게 적용합니다.
     */
    public float[] embedQuery(String query) {
        log.info("[QueryEmbedding] ========== 쿼리 임베딩 시작 (REST API 직접 호출) ==========");
        log.info("[QueryEmbedding] 입력 쿼리: '{}'", query);

        if (query == null || query.isBlank()) {
            log.warn("[QueryEmbedding] 빈 쿼리 입력됨. 빈 벡터 반환.");
            return new float[0];
        }

        if (apiKey == null || apiKey.isBlank()) {
            log.error("[QueryEmbedding] ❌ GOOGLE_API_KEY가 설정되지 않았습니다!");
            return new float[0];
        }

        try {
            // Google Generative AI Embedding API 직접 호출
            String url = String.format(
                "https://generativelanguage.googleapis.com/v1beta/models/%s:embedContent?key=%s",
                modelName, apiKey
            );

            // 요청 바디 구성 - RETRIEVAL_QUERY task-type 명시
            Map<String, Object> requestBody = Map.of(
                "model", "models/" + modelName,
                "content", Map.of("parts", List.of(Map.of("text", query))),
                "taskType", "RETRIEVAL_QUERY"  // 핵심: task-type 명시!
            );

            log.info("[QueryEmbedding] Task-Type: RETRIEVAL_QUERY (REST API 직접 호출)");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getBody() == null) {
                log.error("[QueryEmbedding] ❌ API 응답이 null입니다!");
                return new float[0];
            }

            // 응답에서 임베딩 추출
            Map<String, Object> embedding = (Map<String, Object>) response.getBody().get("embedding");
            if (embedding == null) {
                log.error("[QueryEmbedding] ❌ 응답에 embedding이 없습니다! 응답: {}", response.getBody());
                return new float[0];
            }

            List<Number> values = (List<Number>) embedding.get("values");
            if (values == null || values.isEmpty()) {
                log.error("[QueryEmbedding] ❌ 임베딩 값이 비어있습니다!");
                return new float[0];
            }

            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = values.get(i).floatValue();
            }

            // 벡터 정보 로깅
            double magnitude = 0;
            for (float v : vector) {
                magnitude += v * v;
            }
            magnitude = Math.sqrt(magnitude);

            log.info("[QueryEmbedding] ✅ 벡터 생성 성공!");
            log.info("[QueryEmbedding] - 벡터 차원: {}", vector.length);
            log.info("[QueryEmbedding] - 벡터 크기(magnitude): {}", String.format("%.4f", magnitude));
            log.info("[QueryEmbedding] - 처음 5개 값: {}", Arrays.toString(Arrays.copyOf(vector, Math.min(5, vector.length))));
            log.info("[QueryEmbedding] ========================================");

            return vector;

        } catch (Exception e) {
            log.error("[QueryEmbedding] ❌ 임베딩 생성 중 예외 발생!", e);
            log.error("[QueryEmbedding] 예외 타입: {}, 메시지: {}", e.getClass().getName(), e.getMessage());
            return new float[0];
        }
    }
}
