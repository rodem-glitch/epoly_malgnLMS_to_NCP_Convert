package kr.go.growailms.tutorcontentrecommend.service.dto;

import java.util.Map;

public record TutorContentRecommendResponse(
    String lessonId,
    Long recoContentId,
    String title,
    String categoryNm,
    String summary,
    String keywords,
    double score,
    Map<String, Object> metadata
) {}

