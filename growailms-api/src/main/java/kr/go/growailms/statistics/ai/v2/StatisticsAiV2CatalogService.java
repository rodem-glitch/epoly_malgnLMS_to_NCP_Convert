package kr.go.growailms.statistics.ai.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.go.growailms.statistics.dashboard.service.StatisticsMetaService;
import kr.go.growailms.statistics.internalstats.InternalStatisticsService;
import kr.go.growailms.statistics.mapping.MajorIndustryMappingService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatisticsAiV2CatalogService {
    // 왜: v2는 "자유 조합"을 목표로 하므로, LLM이 어떤 데이터/파라미터가 있는지 먼저 알 수 있게 카탈로그를 분리합니다.

    private static final List<StatisticsAiV2CatalogResponse.AdmRegion> ADM_REGIONS = List.of(
            new StatisticsAiV2CatalogResponse.AdmRegion("11", "서울특별시"),
            new StatisticsAiV2CatalogResponse.AdmRegion("26", "부산광역시"),
            new StatisticsAiV2CatalogResponse.AdmRegion("27", "대구광역시"),
            new StatisticsAiV2CatalogResponse.AdmRegion("28", "인천광역시"),
            new StatisticsAiV2CatalogResponse.AdmRegion("29", "광주광역시"),
            new StatisticsAiV2CatalogResponse.AdmRegion("30", "대전광역시"),
            new StatisticsAiV2CatalogResponse.AdmRegion("31", "울산광역시"),
            new StatisticsAiV2CatalogResponse.AdmRegion("36", "세종특별자치시"),
            new StatisticsAiV2CatalogResponse.AdmRegion("41", "경기도"),
            new StatisticsAiV2CatalogResponse.AdmRegion("42", "강원특별자치도"),
            new StatisticsAiV2CatalogResponse.AdmRegion("43", "충청북도"),
            new StatisticsAiV2CatalogResponse.AdmRegion("44", "충청남도"),
            new StatisticsAiV2CatalogResponse.AdmRegion("45", "전라북도"),
            new StatisticsAiV2CatalogResponse.AdmRegion("46", "전라남도"),
            new StatisticsAiV2CatalogResponse.AdmRegion("47", "경상북도"),
            new StatisticsAiV2CatalogResponse.AdmRegion("48", "경상남도"),
            new StatisticsAiV2CatalogResponse.AdmRegion("50", "제주특별자치도")
    );

    private final ObjectMapper objectMapper;
    private final StatisticsMetaService statisticsMetaService;
    private final MajorIndustryMappingService majorIndustryMappingService;
    private final InternalStatisticsService internalStatisticsService;

    public StatisticsAiV2CatalogService(
            ObjectMapper objectMapper,
            StatisticsMetaService statisticsMetaService,
            MajorIndustryMappingService majorIndustryMappingService,
            InternalStatisticsService internalStatisticsService
    ) {
        this.objectMapper = objectMapper;
        this.statisticsMetaService = statisticsMetaService;
        this.majorIndustryMappingService = majorIndustryMappingService;
        this.internalStatisticsService = internalStatisticsService;
    }

    public StatisticsAiV2CatalogResponse getCatalog() {
        JsonNode root = readCatalogJson();

        List<StatisticsAiV2CatalogResponse.DataSourceSpec> dataSources = readDataSources(root.path("dataSources"));
        List<StatisticsAiV2CatalogResponse.OperationSpec> operations = readOperations(root.path("operations"));
        Map<String, Object> mappings = new LinkedHashMap<>(readObjectMap(root.path("mappings")));
        mappings.put("internalEmploymentAvailableYears", internalStatisticsService.getAvailableEmploymentYears());

        int now = Year.now().getValue();
        List<Integer> years = new ArrayList<>();
        for (int y = now - 1; y >= now - 8; y--) {
            years.add(y);
        }

        List<String> categories = new ArrayList<>(majorIndustryMappingService.getSgisClassCodesByCategory().keySet());

        return new StatisticsAiV2CatalogResponse(
                root.path("version").asText("v2"),
                dataSources,
                operations,
                mappings,
                statisticsMetaService.getCampusGroups(),
                ADM_REGIONS,
                years,
                categories
        );
    }

    private JsonNode readCatalogJson() {
        try (InputStream in = new ClassPathResource("statistics/ai/catalog-v2.json").getInputStream()) {
            return objectMapper.readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("AI 통계 v2 카탈로그 파일을 읽지 못했습니다. classpath:statistics/ai/catalog-v2.json", e);
        }
    }

    private List<StatisticsAiV2CatalogResponse.DataSourceSpec> readDataSources(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();

        List<StatisticsAiV2CatalogResponse.DataSourceSpec> list = new ArrayList<>();
        for (JsonNode n : node) {
            list.add(new StatisticsAiV2CatalogResponse.DataSourceSpec(
                    text(n, "id"),
                    text(n, "name"),
                    text(n, "provider"),
                    text(n, "description"),
                    readStringList(n.path("dimensions")),
                    readStringList(n.path("metrics")),
                    text(n, "notes")
            ));
        }
        return List.copyOf(list);
    }

    private List<StatisticsAiV2CatalogResponse.OperationSpec> readOperations(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();

        List<StatisticsAiV2CatalogResponse.OperationSpec> list = new ArrayList<>();
        for (JsonNode n : node) {
            list.add(new StatisticsAiV2CatalogResponse.OperationSpec(
                    text(n, "id"),
                    text(n, "description"),
                    readStringList(n.path("params"))
            ));
        }
        return List.copyOf(list);
    }

    private String text(JsonNode node, String key) {
        if (node == null) return null;
        String v = node.path(key).asText(null);
        return StringUtils.hasText(v) ? v.trim() : null;
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || node.isNull()) return List.of();
        if (!node.isArray()) return List.of();
        List<String> list = new ArrayList<>();
        for (JsonNode n : node) {
            if (n != null && n.isTextual() && StringUtils.hasText(n.asText())) {
                list.add(n.asText().trim());
            }
        }
        return List.copyOf(list);
    }

    private Map<String, Object> readObjectMap(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) return Map.of();

        Map<String, Object> map = new LinkedHashMap<>();
        node.fields().forEachRemaining(e -> map.put(e.getKey(), jsonNodeToJava(e.getValue())));
        return map;
    }

    private Object jsonNodeToJava(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) return node.asText();
        if (node.isNumber()) return node.numberValue();
        if (node.isBoolean()) return node.booleanValue();
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode n : node) {
                list.add(jsonNodeToJava(n));
            }
            return list;
        }
        if (node.isObject()) {
            return readObjectMap(node);
        }
        return node.asText();
    }
}
