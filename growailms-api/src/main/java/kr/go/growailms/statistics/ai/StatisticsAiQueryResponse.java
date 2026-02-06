package kr.go.growailms.statistics.ai;

import java.util.List;
import java.util.Map;

public record StatisticsAiQueryResponse(
        boolean needClarification,
        String question,
        Map<String, Object> options,
        String message,
        List<String> examples,
        List<ChartSpec> charts,
        TableSpec table,
        String summary,
        List<SourceSpec> sources,
        List<WarningSpec> warnings,
        Map<String, Object> debug
) {
    public record ChartSpec(
            String title,
            String type,
            ChartData data
    ) {
    }

    public record ChartData(
            List<String> labels,
            List<Dataset> datasets
    ) {
    }

    public record Dataset(
            String label,
            List<Double> data,
            String stack
    ) {
    }

    public record TableSpec(
            List<String> columns,
            List<List<Object>> rows
    ) {
    }

    public record SourceSpec(
            String name,
            String note
    ) {
    }

    public record WarningSpec(
            String code,
            String message
    ) {
    }
}

