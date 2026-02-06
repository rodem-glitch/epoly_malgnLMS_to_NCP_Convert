package kr.go.growailms.statistics.ai.v2;

import java.util.List;
import java.util.Map;

public record StatisticsAiV2DataStoreSearchResponse(
        List<Candidate> dataSources,
        List<Candidate> operations,
        Map<String, Object> hints
) {
    public record Candidate(
            String id,
            String name,
            String reason
    ) {
    }
}

