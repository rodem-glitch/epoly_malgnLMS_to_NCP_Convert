package kr.go.growailms.contentsummary.dto;

public record KollusWebhookIngestResponse(
    String mediaContentKey,
    String title,
    String action
) {}

