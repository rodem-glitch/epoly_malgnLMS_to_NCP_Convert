package kr.go.growailms.studentcontentrecommend.service.dto;

import java.util.List;

public record StudentHomeRecommendRequest(
    Long userId,
    Integer siteId,
    String deptName,
    String majorName,
    List<String> courseNames,
    String extraQuery,
    Boolean excludeEnrolled,
    Boolean excludeWatched,
    Boolean excludeCompleted,
    Integer topK,
    Double similarityThreshold
) {
    public static StudentHomeRecommendRequest empty() {
        return new StudentHomeRecommendRequest(null, null, null, null, null, null, null, null, null, null, null);
    }

    public int topKOrDefault() {
        // 왜: 학생 홈 추천은 화면에서 4개만 보여주는 것이 기본이라, default를 4로 둡니다.
        int raw = topK == null ? 4 : topK;
        return Math.max(1, Math.min(raw, 200));
    }

    public double similarityThresholdOrDefault() {
        // 왜: 0.3으로 올리면 관련성 낮은 결과가 제외되어 추천 품질이 올라갑니다.
        double raw = similarityThreshold == null ? 0.3 : similarityThreshold;
        return Math.max(0.0, Math.min(raw, 1.0));
    }

    public boolean excludeEnrolledOrDefault() {
        // 왜: 홈 추천에서는 "아직 수강 안 한(=진도 테이블에 없는) 콘텐츠"를 우선 보여주려는 요구가 많습니다.
        return excludeEnrolled == null ? true : excludeEnrolled;
    }

    public boolean excludeWatchedOrDefault() {
        // 왜: 홈 추천은 짧은 공간이라, 이미 본 영상은 제외하고 새 영상 위주로 보여주는 게 UX가 좋습니다.
        return excludeWatched == null ? true : excludeWatched;
    }

    public boolean excludeCompletedOrDefault() {
        // 왜: "다 본 것(완료)"은 홈 추천에서 제외하고, 더보기에서는 표시만 하도록 합니다.
        return excludeCompleted == null ? true : excludeCompleted;
    }
}
