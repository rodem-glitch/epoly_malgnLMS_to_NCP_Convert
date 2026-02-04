package kr.polytech.lms.statistics.sgis.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.polytech.lms.statistics.kosis.client.KosisClient;
import kr.polytech.lms.statistics.kosis.config.KosisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class SgisClient {
    // 왜: SGIS(OpenAPI3) 통계를 호출하는 로직을 클라이언트로 분리해,
    //     서비스/컨트롤러에서는 "무슨 통계를 어떤 조합으로 계산할지"에만 집중할 수 있게 합니다.

    private final KosisClient kosisClient;
    private final KosisProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private static final Logger log = LoggerFactory.getLogger(SgisClient.class);

    public SgisClient(
            KosisClient kosisClient,
            KosisProperties properties,
            ObjectMapper objectMapper
    ) {
        this.kosisClient = kosisClient;
        this.properties = properties;
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    public CompanyStats fetchCompanyStats(String year, String admCd, String classCode) throws IOException {
        validateCompanyRequest(year, admCd, classCode);

        String accessToken = kosisClient.getAccessToken();
        boolean nationwide = "00".equals(admCd);

        // 왜: SGIS 사업체 통계는 전국(adm_cd=00) 요청을 그대로 보내면 result가 비어(=0으로 계산)지는 케이스가 있어,
        //     전국일 때는 하위 행정구역 결과를 받아 합산(low_search=1)하는 방식으로 처리합니다.
        int lowSearch = nationwide ? 1 : 0;
        if (nationwide) {
            log.debug("SGIS 사업체 통계 전국 조회: year={}, admCd={}, classCode={}, low_search={} (하위 결과 합산)",
                    year, admCd, classCode, lowSearch);
        }

        URI uri = UriComponentsBuilder.fromHttpUrl(properties.getCompanyUrl())
                .queryParam("accessToken", accessToken)
                .queryParam("year", year)
                .queryParam("adm_cd", admCd)
                .queryParam("low_search", lowSearch)
                .queryParam("class_deg", 10)
                .queryParam("class_code", classCode)
                .build(true)
                .toUri();

        String responseBody = restClient.get().uri(uri).retrieve().body(String.class);
        return parseCompanyStatsResponse(responseBody);
    }

    private CompanyStats parseCompanyStatsResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(Objects.requireNonNullElse(responseBody, ""));

        int errCd = root.path("errCd").asInt(0);
        if (errCd != 0) {
            String errMsg = root.path("errMsg").asText("Unknown error");
            throw new IllegalStateException("SGIS 사업체 통계 호출에 실패했습니다. (" + errCd + ") " + errMsg);
        }

        JsonNode result = root.path("result");
        if (!result.isArray() || result.isEmpty()) {
            return new CompanyStats(null, null);
        }

        List<Long> corpCnts = new ArrayList<>();
        List<Long> totWorkers = new ArrayList<>();
        for (JsonNode node : result) {
            corpCnts.add(parseNullableLong(node.path("corp_cnt").asText(null)));
            totWorkers.add(parseNullableLong(node.path("tot_worker").asText(null)));
        }

        Long corpCnt = sumNullableLongs(corpCnts);
        Long totWorker = sumNullableLongs(totWorkers);
        return new CompanyStats(corpCnt, totWorker);
    }

    private void validateCompanyRequest(String year, String admCd, String classCode) {
        // 왜: 잘못된 파라미터로 외부 API를 때리면 불필요한 호출이 늘고, 디버깅도 어려워집니다.
        if (!StringUtils.hasText(properties.getCompanyUrl())) {
            throw new IllegalStateException("SGIS company-url 설정이 없습니다. kosis.company-url 을 확인해 주세요.");
        }
        if (!StringUtils.hasText(year)) {
            throw new IllegalArgumentException("year가 비어 있습니다.");
        }
        if (!StringUtils.hasText(admCd)) {
            throw new IllegalArgumentException("admCd가 비어 있습니다.");
        }
        if (!StringUtils.hasText(classCode)) {
            throw new IllegalArgumentException("classCode가 비어 있습니다.");
        }
    }

    private Long parseNullableLong(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }

        String normalized = raw.trim();
        if ("N/A".equalsIgnoreCase(normalized)) {
            return null;
        }

        String digitsOnly = normalized.replace(",", "");
        try {
            return Long.parseLong(digitsOnly);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("SGIS 숫자 파싱에 실패했습니다. 값=" + normalized, e);
        }
    }

    private Long sumNullableLongs(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        boolean hasAny = false;
        long sum = 0L;
        for (Long v : values) {
            if (v == null) continue;
            hasAny = true;
            sum += v;
        }
        return hasAny ? sum : null;
    }

    public record CompanyStats(Long corpCnt, Long totWorker) {
    }
}
