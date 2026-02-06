package kr.go.growailms.statistics.dashboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.go.growailms.statistics.dashboard.persistence.StatisticsDashboardCacheJdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class StatisticsDashboardCacheService {
    // 왜: 산업/인구 비교 결과의 캐시 정책을 한 곳에서 관리하면 서비스 코드가 단순해지고 유지보수가 쉬워집니다.
    private static final Logger log = LoggerFactory.getLogger(StatisticsDashboardCacheService.class);

    private static final String CACHE_TYPE_INDUSTRY = "industry";
    private static final String CACHE_TYPE_POPULATION = "population";

    private final StatisticsDashboardCacheJdbcRepository cacheJdbcRepository;
    private final ObjectMapper objectMapper;

    public StatisticsDashboardCacheService(
            StatisticsDashboardCacheJdbcRepository cacheJdbcRepository,
            ObjectMapper objectMapper
    ) {
        this.cacheJdbcRepository = cacheJdbcRepository;
        this.objectMapper = objectMapper;
    }

    public Optional<IndustryAnalysisService.IndustryAnalysisResponse> findIndustry(
            String campus,
            String admCd,
            String admNm,
            Integer statsYear
    ) {
        Optional<String> payloadOpt = cacheJdbcRepository.findPayloadJson(
                CACHE_TYPE_INDUSTRY,
                campus,
                admCd,
                admNm,
                statsYear
        );
        if (payloadOpt.isEmpty()) {
            return Optional.empty();
        }

        try {
            IndustryAnalysisService.IndustryAnalysisResponse response = objectMapper.readValue(
                    payloadOpt.get(),
                    IndustryAnalysisService.IndustryAnalysisResponse.class
            );
            return Optional.of(response);
        } catch (JsonProcessingException e) {
            log.error("산업 통계 캐시 JSON 파싱 실패", e);
            throw new IllegalStateException("산업 통계 캐시 데이터 파싱에 실패했습니다.", e);
        }
    }

    public void saveIndustry(
            String campus,
            String admCd,
            String admNm,
            Integer statsYear,
            IndustryAnalysisService.IndustryAnalysisResponse response
    ) {
        String payload = toJson(response, "산업 통계");
        cacheJdbcRepository.upsertPayloadJson(
                CACHE_TYPE_INDUSTRY,
                campus,
                admCd,
                admNm,
                statsYear,
                payload
        );
    }

    public Optional<PopulationComparisonService.PopulationComparisonResponse> findPopulation(
            String campus,
            String admCd,
            String admNm,
            Integer populationYear
    ) {
        Optional<String> payloadOpt = cacheJdbcRepository.findPayloadJson(
                CACHE_TYPE_POPULATION,
                campus,
                admCd,
                admNm,
                populationYear
        );
        if (payloadOpt.isEmpty()) {
            return Optional.empty();
        }

        try {
            PopulationComparisonService.PopulationComparisonResponse response = objectMapper.readValue(
                    payloadOpt.get(),
                    PopulationComparisonService.PopulationComparisonResponse.class
            );
            return Optional.of(response);
        } catch (JsonProcessingException e) {
            log.error("인구 통계 캐시 JSON 파싱 실패", e);
            throw new IllegalStateException("인구 통계 캐시 데이터 파싱에 실패했습니다.", e);
        }
    }

    public void savePopulation(
            String campus,
            String admCd,
            String admNm,
            Integer populationYear,
            PopulationComparisonService.PopulationComparisonResponse response
    ) {
        String payload = toJson(response, "인구 통계");
        cacheJdbcRepository.upsertPayloadJson(
                CACHE_TYPE_POPULATION,
                campus,
                admCd,
                admNm,
                populationYear,
                payload
        );
    }

    private String toJson(Object response, String label) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.error("{} 캐시 JSON 직렬화 실패", label, e);
            throw new IllegalStateException(label + " 캐시 데이터 직렬화에 실패했습니다.", e);
        }
    }
}

