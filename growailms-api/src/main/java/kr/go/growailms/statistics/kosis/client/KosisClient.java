package kr.go.growailms.statistics.kosis.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.go.growailms.statistics.kosis.client.dto.KosisPopulationRow;
import kr.go.growailms.statistics.kosis.config.KosisProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class KosisClient {
    // 왜: KOSIS API 호출/토큰 관리를 한 곳에 모아(클라이언트) 컨트롤러/서비스를 단순화합니다.

    private final KosisProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private final AtomicReference<KosisToken> cachedToken = new AtomicReference<>();

    public KosisClient(
            KosisProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
        this.clock = Clock.systemUTC();
    }

    public List<KosisPopulationRow> fetchPopulation(String year, String ageType, String gender) throws IOException {
        return fetchPopulation(year, ageType, gender, null);
    }

    public List<KosisPopulationRow> fetchPopulation(String year, String ageType, String gender, String admCd) throws IOException {
        // 왜: 토큰 만료를 신경 쓰지 않도록, 호출 시점에 자동으로 토큰을 확보합니다.
        String accessToken = getAccessToken();

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(properties.getPopulationUrl())
                .queryParam("accessToken", accessToken)
                .queryParam("year", year)
                .queryParam("age_type", ageType)
                .queryParam("gender", gender);

        if (StringUtils.hasText(admCd)) {
            // 왜: 특정 행정구역만 필요할 때 전체 목록을 받지 않아도 되도록 필터 파라미터를 지원합니다.
            builder.queryParam("adm_cd", admCd.trim());
        }

        URI uri = builder.build(true).toUri();

        String responseBody = restClient.get().uri(uri).retrieve().body(String.class);

        return parsePopulationResponse(responseBody);
    }

    public String getAccessToken() throws IOException {
        // 왜: 인구 외(산업 등) 다른 SGIS API에서도 동일 토큰을 재사용하기 위해, 토큰 문자열만 노출합니다.
        return getOrRefreshToken().accessToken();
    }

    private KosisToken getOrRefreshToken() throws IOException {
        KosisToken existing = cachedToken.get();
        if (existing != null && existing.isValid(clock.millis())) {
            return existing;
        }

        KosisToken refreshed = requestNewToken();
        cachedToken.set(refreshed);
        return refreshed;
    }

    private KosisToken requestNewToken() throws IOException {
        validateKosisCredentials();

        URI uri = UriComponentsBuilder.fromHttpUrl(properties.getAuthUrl())
                .queryParam("consumer_key", properties.getConsumerKey())
                .queryParam("consumer_secret", properties.getConsumerSecret())
                .build(true)
                .toUri();

        String responseBody = restClient.get().uri(uri).retrieve().body(String.class);

        return parseTokenResponse(responseBody);
    }

    private void validateKosisCredentials() {
        // 왜: 키/시크릿이 없을 때는 KOSIS 호출 자체가 불가능하므로, 빠르게 명확한 에러를 냅니다.
        if (!StringUtils.hasText(properties.getConsumerKey()) || !StringUtils.hasText(properties.getConsumerSecret())) {
            throw new IllegalStateException("KOSIS 설정이 없습니다. 환경변수 KOSIS_CONSUMER_KEY / KOSIS_CONSUMER_SECRET 를 설정해 주세요.");
        }
        if (!StringUtils.hasText(properties.getAuthUrl()) || !StringUtils.hasText(properties.getPopulationUrl())) {
            throw new IllegalStateException("KOSIS URL 설정이 비어 있습니다. kosis.auth-url / kosis.population-url 을 확인해 주세요.");
        }
    }

    private KosisToken parseTokenResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(Objects.requireNonNullElse(responseBody, ""));
        JsonNode result = root.path("result");

        String accessToken = result.path("accessToken").asText(null);
        long accessTimeout = result.path("accessTimeout").asLong(0);

        if (!StringUtils.hasText(accessToken) || accessTimeout <= 0) {
            throw new IllegalStateException("KOSIS 토큰 응답 파싱에 실패했습니다. 응답 형식을 확인해 주세요.");
        }

        return new KosisToken(accessToken, accessTimeout);
    }

    private List<KosisPopulationRow> parsePopulationResponse(String responseBody) throws IOException {
        // 왜: KOSIS/SGIS는 에러 응답(예: errCd/errMsg)이거나 result가 없는 응답이 올 수 있습니다.
        //     기존 구현은 result가 없을 때 JsonToken.NOT_AVAILABLE로 역직렬화가 터져서, 원인 파악이 어렵습니다.
        if (!StringUtils.hasText(responseBody)) {
            return List.of();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (IOException e) {
            // 왜: 간혹 HTML(차단/오류 페이지)이나 깨진 JSON이 내려오면, 응답 본문을 안 보면 원인을 모릅니다.
            throw new IllegalStateException("KOSIS 인구 응답이 JSON이 아닙니다. 응답=" + safeTruncate(responseBody, 500), e);
        }
        if (root == null || root.isNull() || root.isMissingNode()) {
            return List.of();
        }

        // 1) 에러 응답 방어 (SGIS/KOSIS 공통 형태)
        String errCd = textAny(root, List.of("errCd", "err_cd", "errorCode", "error_code"));
        String errMsg = textAny(root, List.of("errMsg", "err_msg", "errorMsg", "error_msg", "message"));
        if (StringUtils.hasText(errCd) && !"0".equals(errCd.trim())) {
            throw new IllegalStateException("KOSIS 인구 조회에 실패했습니다. errCd=" + errCd.trim()
                    + (StringUtils.hasText(errMsg) ? (", errMsg=" + errMsg.trim()) : ""));
        }

        // 2) 정상 응답은 보통 {"result":[...]} 형태지만, 환경에 따라 배열이 루트로 올 수도 있어 폴백을 둡니다.
        JsonNode resultNode = root.path("result");
        if (resultNode.isMissingNode() || resultNode.isNull()) {
            if (root.isArray()) {
                resultNode = root;
            }
        }

        if (resultNode == null || !resultNode.isArray()) {
            throw new IllegalStateException("KOSIS 인구 응답에 result 배열이 없습니다. 응답=" + safeTruncate(root.toString(), 500));
        }

        return objectMapper.readValue(
                objectMapper.treeAsTokens(resultNode),
                new TypeReference<>() {
                }
        );
    }

    private String textAny(JsonNode root, List<String> keys) {
        if (root == null || keys == null) return null;
        for (String k : keys) {
            if (!StringUtils.hasText(k)) continue;
            JsonNode v = root.get(k);
            if (v != null && v.isValueNode()) {
                String s = v.asText(null);
                if (StringUtils.hasText(s)) {
                    return s;
                }
            }
        }
        return null;
    }

    private String safeTruncate(String text, int max) {
        if (text == null) return null;
        if (text.length() <= max) return text;
        return text.substring(0, max) + "...";
    }

    private record KosisToken(String accessToken, long accessTimeoutEpochMs) {
        boolean isValid(long nowEpochMs) {
            // 왜: 레거시와 동일하게 "현재 시각 < 만료 시각" 기준으로 유효성을 판단합니다.
            return StringUtils.hasText(accessToken) && nowEpochMs < accessTimeoutEpochMs;
        }
    }
}
