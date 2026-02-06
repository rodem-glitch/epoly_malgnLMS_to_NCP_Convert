package kr.go.growailms.contentsummary.dto;

public record RunTranscriptionItemResult(
    String mediaContentKey,
    String title,
    String result,
    String message
) {}

