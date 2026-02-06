package kr.go.growailms.statistics.ai.v2;

public final class StatisticsAiV2Ops {
    private StatisticsAiV2Ops() {
    }

    // 왜: LLM이 생성한 "실행계획"은 반드시 서버 allowlist 안에서만 실행되어야 안전합니다.
    public static final String SGIS_METRIC_SERIES = "SGIS_METRIC_SERIES";
    public static final String KOSIS_POPULATION_SERIES = "KOSIS_POPULATION_SERIES";
    public static final String INTERNAL_EMPLOYMENT_TOP = "INTERNAL_EMPLOYMENT_TOP";
    public static final String INTERNAL_ADMISSION_TOP = "INTERNAL_ADMISSION_TOP";
    public static final String INTERNAL_EMPLOYMENT_SERIES = "INTERNAL_EMPLOYMENT_SERIES";

    public static final String CHEMIST_CORRELATION = "CHEMIST_CORRELATION";
    public static final String CHEMIST_GROWTH_RATE = "CHEMIST_GROWTH_RATE";
    public static final String CHEMIST_DELTA_POINTS = "CHEMIST_DELTA_POINTS";

    public static final String DESIGNER_CHART = "DESIGNER_CHART";
}
