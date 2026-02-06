package kr.go.growailms.contentsummary.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 왜: 전사/요약은 비용과 시간이 큰 작업이라, 운영에서 "자동 실행 여부/속도"를 환경변수로 제어해야 안전합니다.
 */
@ConfigurationProperties(prefix = "contentsummary.worker")
public record ContentSummaryWorkerProperties(
    boolean enabled,
    long pollDelayMs,
    int batchSize,
    int maxRetries,
    int retryDelaySeconds,
    int processingTimeoutSeconds
) {
    public ContentSummaryWorkerProperties {
        pollDelayMs = pollDelayMs <= 0 ? 30_000L : pollDelayMs;
        batchSize = batchSize <= 0 ? 3 : Math.min(batchSize, 50);
        maxRetries = maxRetries < 0 ? 0 : maxRetries;
        retryDelaySeconds = retryDelaySeconds < 0 ? 0 : retryDelaySeconds;
        processingTimeoutSeconds = processingTimeoutSeconds <= 0 ? 7200 : processingTimeoutSeconds;
    }
}
