package kr.go.growailms.studentcontentrecommend.service.dto;

public record StudentSearchRecommendRequest(
    Long userId,
    Integer siteId,
    String query,
    Integer topK,
    Double similarityThreshold
) {
    public static StudentSearchRecommendRequest empty() {
        return new StudentSearchRecommendRequest(null, null, null, null, null);
    }

    public int topKOrDefault() {
        int raw = topK == null ? 50 : topK;
        return Math.max(1, Math.min(raw, 200));
    }

    public double similarityThresholdOrDefault() {
        // 왜: 0.3으로 올리면 관련성 낮은 결과가 제외되어 검색 품질이 올라갑니다.
        double raw = similarityThreshold == null ? 0.3 : similarityThreshold;
        return Math.max(0.0, Math.min(raw, 1.0));
    }
}
