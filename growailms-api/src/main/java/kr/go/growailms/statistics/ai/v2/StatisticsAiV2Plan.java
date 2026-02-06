package kr.go.growailms.statistics.ai.v2;

import java.util.List;
import java.util.Map;

public record StatisticsAiV2Plan(
        Action action,
        String question,
        List<String> fields,
        String message,
        List<String> examples,
        List<Step> steps,
        String rawJson
) {
    public enum Action {EXECUTE, CLARIFY, UNSUPPORTED}

    public enum Agent {ANALYST, CHEMIST, DESIGNER}

    public record Step(
            String id,
            Agent agent,
            String op,
            String as,
            Map<String, Object> params
    ) {
    }
}

