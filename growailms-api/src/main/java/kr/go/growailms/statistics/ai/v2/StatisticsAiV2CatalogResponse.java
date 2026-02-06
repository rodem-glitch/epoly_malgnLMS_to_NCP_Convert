package kr.go.growailms.statistics.ai.v2;

import kr.go.growailms.statistics.mapping.MajorIndustryMappingService;

import java.util.List;
import java.util.Map;

public record StatisticsAiV2CatalogResponse(
        String version,
        List<DataSourceSpec> dataSources,
        List<OperationSpec> operations,
        Map<String, Object> mappings,
        List<MajorIndustryMappingService.CampusGroup> campusGroups,
        List<AdmRegion> admRegions,
        List<Integer> recommendedYears,
        List<String> industryCategories
) {
    public record DataSourceSpec(
            String id,
            String name,
            String provider,
            String description,
            List<String> dimensions,
            List<String> metrics,
            String notes
    ) {
    }

    public record OperationSpec(
            String id,
            String description,
            List<String> params
    ) {
    }

    public record AdmRegion(
            String admCd,
            String name
    ) {
    }
}

