package kr.go.growailms.statistics.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GeminiClient {
    // 왜: Spring AI(ChatModel)를 붙이면 설정/의존성이 프로젝트마다 달라질 수 있어,
    //     v1에서는 Google Generative Language API를 REST로 직접 호출해 동작을 단순화합니다.
    //     (LLM은 실행계획(JSON) 생성만 하고, 실제 수치 계산은 서버가 수행합니다)

    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";

    private final StatisticsAiProperties properties;
    private final RestClient restClient;

    public GeminiClient(StatisticsAiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(DEFAULT_BASE_URL)
                .build();
    }

    public String generateText(String prompt) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("AI 통계 기능이 비활성화되어 있습니다. (statistics.ai.enabled=false)");
        }

        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("AI 통계 API 키가 설정되지 않았습니다. 환경변수 GOOGLE_API_KEY 또는 STATISTICS_AI_API_KEY를 설정해 주세요.");
        }

        String model = StringUtils.hasText(properties.getModel()) ? properties.getModel().trim() : "gemini-1.5-flash";
        String path = "/v1beta/models/" + model + ":generateContent";

        // 왜: LLM이 종종 JSON 앞뒤로 설명을 섞거나, 괄호를 닫지 못해 파싱이 깨질 수 있습니다.
        //     공식 문서의 Structured Output 옵션(responseMimeType/responseSchema)으로 "JSON만" 반환하도록 강제합니다.
        // 참고(공식): https://ai.google.dev/api/rest/v1beta/GenerationConfig
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", 0.1);
        // 왜: 실행계획(JSON)이 길어질 수 있어, 잘림을 줄이기 위해 토큰 상한을 늘립니다.
        generationConfig.put("maxOutputTokens", 8192);
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", buildPlanResponseSchema());

        Map<String, Object> body = Map.of(
                "contents", new Object[]{
                        Map.of(
                                "role", "user",
                                "parts", new Object[]{Map.of("text", prompt)}
                        )
                },
                "generationConfig", generationConfig
        );

        JsonNode response = restClient.post()
                .uri(uriBuilder -> uriBuilder.path(path).queryParam("key", properties.getApiKey()).build())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("Gemini 응답이 비어 있습니다.");
        }

        JsonNode candidates = response.get("candidates");
        if (candidates == null || !candidates.isArray() || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini 응답에 candidates가 없습니다. 응답=" + safeTruncate(response.toString(), 500));
        }

        JsonNode textNode = candidates.path(0).path("content").path("parts").path(0).path("text");
        if (textNode == null || !textNode.isTextual() || !StringUtils.hasText(textNode.asText())) {
            throw new IllegalStateException("Gemini 응답에서 텍스트를 찾지 못했습니다. 응답=" + safeTruncate(response.toString(), 500));
        }

        return textNode.asText();
    }

    private Map<String, Object> buildPlanResponseSchema() {
        // 왜: v2는 "실행계획(JSON)"을 파싱해 동작하므로, 가능한 한 엄격한 스키마로 유효 JSON을 받습니다.
        //     ※ responseSchema는 "표준 JSON Schema"가 아니라 Gemini API의 Schema 포맷이라
        //        additionalProperties 같은 필드가 지원되지 않습니다(400 발생).
        //     그래서 params는 '지원하는 키들의 합집합'으로 정의해, 모델이 그 범위 안에서만 JSON을 만들게 유도합니다.

        Map<String, Object> paramProperties = new LinkedHashMap<>();
        paramProperties.put("admCd", Map.of("type", "string"));
        paramProperties.put("years", Map.of("type", "array", "items", Map.of("type", "number")));
        paramProperties.put("metric", Map.of("type", "string"));
        paramProperties.put("category", Map.of("type", "string"));
        paramProperties.put("classCodes", Map.of("type", "array", "items", Map.of("type", "string")));
        paramProperties.put("ageType", Map.of("type", "string"));
        paramProperties.put("gender", Map.of("type", "string"));
        paramProperties.put("campus", Map.of("type", "string"));
        paramProperties.put("dept", Map.of("type", "string"));
        paramProperties.put("top", Map.of("type", "number"));
        paramProperties.put("chartType", Map.of("type", "string"));
        paramProperties.put("title", Map.of("type", "string"));
        paramProperties.put("seriesRefs", Map.of("type", "array", "items", Map.of("type", "string")));
        paramProperties.put("seriesRef", Map.of("type", "string"));
        paramProperties.put("xRef", Map.of("type", "string"));
        paramProperties.put("yRef", Map.of("type", "string"));
        paramProperties.put("leftRef", Map.of("type", "string"));
        paramProperties.put("rightRef", Map.of("type", "string"));

        Map<String, Object> stepProperties = new LinkedHashMap<>();
        stepProperties.put("id", Map.of("type", "string"));
        stepProperties.put("agent", Map.of("type", "string"));
        stepProperties.put("op", Map.of("type", "string"));
        stepProperties.put("as", Map.of("type", "string"));
        stepProperties.put("params", Map.of("type", "object", "properties", paramProperties));

        Map<String, Object> stepSchema = new LinkedHashMap<>();
        stepSchema.put("type", "object");
        stepSchema.put("properties", stepProperties);
        stepSchema.put("required", List.of("id", "agent", "op"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("action", Map.of("type", "string"));
        properties.put("question", Map.of("type", "string"));
        properties.put("fields", Map.of("type", "array", "items", Map.of("type", "string")));
        properties.put("message", Map.of("type", "string"));
        properties.put("examples", Map.of("type", "array", "items", Map.of("type", "string")));
        properties.put("steps", Map.of("type", "array", "items", stepSchema));
        schema.put("properties", properties);
        schema.put("required", List.of("action"));
        return schema;
    }

    private String safeTruncate(String text, int max) {
        if (text == null) return null;
        if (text.length() <= max) return text;
        return text.substring(0, max) + "...";
    }
}
