package kr.go.growailms.tutorcontentrecommend.service.dto;

import java.util.List;

public record TutorContentRecommendRequest(
    String courseName,
    String courseIntro,
    String courseDetail,
    String lessonTitle,
    String lessonDescription,
    String keywords,
    Integer topK,
    Double similarityThreshold
) {
    public static TutorContentRecommendRequest empty() {
        return new TutorContentRecommendRequest(null, null, null, null, null, null, null, null);
    }

    public int topKOrDefault() {
        int raw = topK == null ? 10 : topK;
        return Math.max(1, Math.min(raw, 50));
    }

    public double similarityThresholdOrDefault() {
        // 왜: 0.3으로 올리면 관련성 낮은 결과가 제외되어 추천 품질이 올라갑니다.
        double raw = similarityThreshold == null ? 0.3 : similarityThreshold;
        return Math.max(0.0, Math.min(raw, 1.0));
    }
}
