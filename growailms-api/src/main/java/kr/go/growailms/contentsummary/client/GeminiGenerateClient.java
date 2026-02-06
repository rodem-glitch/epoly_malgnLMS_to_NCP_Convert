package kr.go.growailms.contentsummary.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 왜: Spring AI에 "임베딩" 스타터만 들어가 있어서, 요약/키워드 생성은 Gemini REST API를 직접 호출합니다.
 */
@Component
public class GeminiGenerateClient {

    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiGenerateClient(GeminiProperties properties, ObjectMapper objectMapper) {
        this.properties = Objects.requireNonNull(properties);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(safeTimeout(properties.httpTimeout(), Duration.ofSeconds(60)))
            .build();
    }

    public String generateText(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt가 비어 있습니다.");
        }
        String apiKey = properties.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("gemini.api-key 설정이 필요합니다. (환경변수 또는 application-local.yml)");
        }

        URI uri = URI.create(properties.baseUrl()
            + "/models/"
            + urlEncode(properties.model())
            + ":generateContent?key="
            + urlEncode(apiKey.trim()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(
            Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", prompt))
            )
        ));
        body.put("generationConfig", Map.of(
            "temperature", properties.temperature(),
            "maxOutputTokens", properties.maxOutputTokens()
        ));

        String jsonBody = toJson(body);

        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(safeTimeout(properties.httpTimeout(), Duration.ofSeconds(60)))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
            .build();

        String responseBody = send(request);
        return extractCandidateText(responseBody);
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Gemini API 호출 실패 (HTTP " + status + ")");
            }
            return response.body();
        } catch (Exception e) {
            throw new IllegalStateException("Gemini API 통신 중 오류가 발생했습니다.", e);
        }
    }

    private String extractCandidateText(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            // 왜: parts가 여러 개로 내려오면 parts[0]만 읽을 때 응답이 중간에서 끊길 수 있습니다.
            JsonNode parts = root.at("/candidates/0/content/parts");
            if (parts.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode part : parts) {
                    if (part == null || part.isNull()) continue;
                    JsonNode text = part.get("text");
                    if (text == null || text.isNull()) continue;
                    sb.append(text.asText(""));
                }
                return sb.toString().trim();
            }

            JsonNode text = root.at("/candidates/0/content/parts/0/text");
            if (text.isMissingNode() || text.isNull()) return "";
            return text.asText("").trim();
        } catch (Exception e) {
            throw new IllegalStateException("Gemini 응답 파싱에 실패했습니다.", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Gemini 요청 JSON 생성에 실패했습니다.", e);
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static Duration safeTimeout(Duration configured, Duration fallback) {
        if (configured == null) return fallback;
        if (configured.isZero() || configured.isNegative()) return fallback;
        return configured;
    }
}

