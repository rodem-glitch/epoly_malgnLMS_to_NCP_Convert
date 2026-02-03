package kr.polytech.lms.statistics.sgis.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.polytech.lms.statistics.kosis.client.KosisClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Objects;

@Component
public class SgisAddressClient {
    // 왜: SGIS 통계 API는 "행정구역 코드(adm_cd)"를 사용합니다.
    //     그런데 프론트(캠퍼스 매핑)는 행안부(41/28/30...) 코드 체계를 쓰고, SGIS는 31/23/25... 코드 체계를 씁니다.
    //     그래서 '이름(예: 경기도 성남시 수정구)' 기준으로 SGIS stage 코드(cd)를 찾아 adm_cd로 변환해줘야 합니다.

    private static final String STAGE_URL = "https://sgisapi.mods.go.kr/OpenAPI3/addr/stage.json";

    private final KosisClient kosisClient;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public SgisAddressClient(
            KosisClient kosisClient,
            ObjectMapper objectMapper
    ) {
        this.kosisClient = kosisClient;
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    public List<StageRow> fetchStages(String parentCd) throws IOException {
        String accessToken = kosisClient.getAccessToken();

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(STAGE_URL)
                .queryParam("accessToken", accessToken);
        if (StringUtils.hasText(parentCd)) {
            builder.queryParam("cd", parentCd.trim());
        }

        URI uri = builder.build(true).toUri();

        String responseBody = restClient.get().uri(uri).retrieve().body(String.class);
        return parseStageResponse(responseBody);
    }

    private List<StageRow> parseStageResponse(String responseBody) throws IOException {
        if (!StringUtils.hasText(responseBody)) {
            return List.of();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(Objects.requireNonNullElse(responseBody, ""));
        } catch (IOException e) {
            throw new IllegalStateException("SGIS stage 응답이 JSON이 아닙니다. 응답=" + safeTruncate(responseBody, 500), e);
        }

        String errCd = root.path("errCd").asText(null);
        String errMsg = root.path("errMsg").asText(null);
        if (StringUtils.hasText(errCd) && !"0".equals(errCd.trim())) {
            throw new IllegalStateException("SGIS stage 조회가 실패했습니다. errCd=" + errCd.trim()
                    + (StringUtils.hasText(errMsg) ? (", errMsg=" + errMsg.trim()) : ""));
        }

        JsonNode resultNode = root.path("result");
        if (resultNode.isMissingNode() || resultNode.isNull()) {
            return List.of();
        }
        if (!resultNode.isArray()) {
            throw new IllegalStateException("SGIS stage 응답의 result가 배열이 아닙니다. 응답=" + safeTruncate(root.toString(), 500));
        }

        return objectMapper.readValue(
                objectMapper.treeAsTokens(resultNode),
                new TypeReference<>() {
                }
        );
    }

    private String safeTruncate(String text, int max) {
        if (text == null) return null;
        if (text.length() <= max) return text;
        return text.substring(0, max) + "...";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StageRow(
            @JsonProperty("cd") String cd,
            @JsonProperty("addr_name") String addrName,
            @JsonProperty("full_addr") String fullAddr
    ) {
    }
}

