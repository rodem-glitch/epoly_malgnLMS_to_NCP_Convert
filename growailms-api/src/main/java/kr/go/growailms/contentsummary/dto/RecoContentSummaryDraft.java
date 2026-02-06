package kr.go.growailms.contentsummary.dto;

import java.util.List;

public record RecoContentSummaryDraft(
    String categoryNm,
    String summary,
    List<String> keywords
) {}

