package kr.go.growailms.contentsummary.dto;

/**
 * 왜: Kollus 채널 목록 API 응답은 필드가 많고/버전에 따라 조금씩 달라집니다.
 * 전사 파이프라인에서 필요한 최소 필드만 따로 DTO로 고정해두면, 나중에 API가 바뀌어도 영향 범위를 줄일 수 있습니다.
 */
public record KollusChannelContent(
    String mediaContentKey,
    String title,
    Integer totalTimeSeconds
) {}

