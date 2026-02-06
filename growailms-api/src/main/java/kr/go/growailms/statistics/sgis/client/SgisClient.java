package kr.go.growailms.statistics.sgis.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.go.growailms.statistics.kosis.client.KosisClient;
import kr.go.growailms.statistics.kosis.config.KosisProperties;
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
    // ?? SGIS(OpenAPI3) ?듦퀎瑜??몄텧?섎뒗 濡쒖쭅???대씪?댁뼵?몃줈 遺꾨━??
    //     ?쒕퉬??而⑦듃濡ㅻ윭?먯꽌??"臾댁뒯 ?듦퀎瑜??대뼡 議고빀?쇰줈 怨꾩궛?좎?"?먮쭔 吏묒쨷?????덇쾶 ?⑸땲??

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
        boolean nationwide = "00".equals(admCd) || !StringUtils.hasText(admCd);

        // ?? SGIS ?ъ뾽泥??듦퀎???꾧뎅(adm_cd=00) ?붿껌??洹몃?濡?蹂대궡硫?result媛 鍮꾩뼱(=0?쇰줈 怨꾩궛)吏??耳?댁뒪媛 ?덉뼱,
        //     ?꾧뎅???뚮뒗 ?섏쐞 ?됱젙援ъ뿭 寃곌낵瑜?諛쏆븘 ?⑹궛(low_search=1)?섎뒗 諛⑹떇?쇰줈 泥섎━?⑸땲??
        int lowSearch = nationwide ? 1 : 0;
        if (nationwide) {
            log.debug("SGIS ?ъ뾽泥??듦퀎 ?꾧뎅 議고쉶: year={}, admCd={}, classCode={}, low_search={} (?섏쐞 寃곌낵 ?⑹궛)",
                    year, admCd, classCode, lowSearch);
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(properties.getCompanyUrl())
                .queryParam("accessToken", accessToken)
                .queryParam("year", year)
                .queryParam("class_deg", 10)
                .queryParam("class_code", classCode);

        if (StringUtils.hasText(admCd)) {
            builder.queryParam("adm_cd", admCd);
        }
        if (nationwide || StringUtils.hasText(admCd)) {
            // 왜: 문서상 adm_cd 미전달(non)일 때도 전국/시도 목록 조회는 low_search=1 기준이므로, 의도를 명시합니다.
            builder.queryParam("low_search", lowSearch);
        }

        URI uri = builder.build(true).toUri();

        String responseBody = restClient.get().uri(uri).retrieve().body(String.class);
        return parseCompanyStatsResponse(responseBody);
    }

    private CompanyStats parseCompanyStatsResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(Objects.requireNonNullElse(responseBody, ""));

        int errCd = root.path("errCd").asInt(0);
        if (errCd != 0) {
            String errMsg = root.path("errMsg").asText("Unknown error");
            throw new IllegalStateException("SGIS ?ъ뾽泥??듦퀎 ?몄텧???ㅽ뙣?덉뒿?덈떎. (" + errCd + ") " + errMsg);
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
        // ?? ?섎せ???뚮씪誘명꽣濡??몃? API瑜??뚮━硫?遺덊븘?뷀븳 ?몄텧???섍퀬, ?붾쾭源낅룄 ?대젮?뚯쭛?덈떎.
        if (!StringUtils.hasText(properties.getCompanyUrl())) {
            throw new IllegalStateException("SGIS company-url ?ㅼ젙???놁뒿?덈떎. kosis.company-url ???뺤씤??二쇱꽭??");
        }
        if (!StringUtils.hasText(year)) {
            throw new IllegalArgumentException("year媛 鍮꾩뼱 ?덉뒿?덈떎.");
        }
        // 왜: adm_cd 미전달(non)도 문서상 유효하며(전국 시도 리스트), 전국 합산에 사용합니다.
        if (!StringUtils.hasText(classCode)) {
            throw new IllegalArgumentException("classCode媛 鍮꾩뼱 ?덉뒿?덈떎.");
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
            throw new IllegalStateException("SGIS ?レ옄 ?뚯떛???ㅽ뙣?덉뒿?덈떎. 媛?" + normalized, e);
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

