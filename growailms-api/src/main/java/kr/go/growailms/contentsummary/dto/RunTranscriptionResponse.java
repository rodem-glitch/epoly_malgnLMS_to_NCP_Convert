package kr.go.growailms.contentsummary.dto;

import java.util.List;

public record RunTranscriptionResponse(
    int processed,
    int skipped,
    int failed,
    List<RunTranscriptionItemResult> items
) {}

