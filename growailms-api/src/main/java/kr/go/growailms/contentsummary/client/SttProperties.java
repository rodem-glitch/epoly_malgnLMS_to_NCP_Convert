package kr.go.growailms.contentsummary.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 왜: 전사(STT)는 외부 API를 쓰는 경우가 많고, 제공자/모델/언어가 바뀔 수 있어서 설정으로 분리합니다.
 */
@ConfigurationProperties(prefix = "stt")
public record SttProperties(
    String provider,
    String language,
    Google google,
    Duration httpTimeout
) {
    public SttProperties {
        provider = (provider == null || provider.isBlank()) ? "google" : provider.trim();
        language = (language == null || language.isBlank()) ? "ko" : language.trim();
        google = google == null ? new Google(null, null, null, null, null, false, true, null, null, null) : google;
        httpTimeout = safeTimeout(httpTimeout, Duration.ofMinutes(5));
    }

    public record Google(
        String projectId,
        String location,
        String recognizerId,
        String gcsBucket,
        String gcsPrefix,
        boolean keepUploadedFiles,
        boolean enableAutomaticPunctuation,
        Duration pollingInterval,
        Duration pollingTimeout,
        String model
    ) {
        public Google {
            location = (location == null || location.isBlank()) ? "global" : location.trim();
            recognizerId = (recognizerId == null || recognizerId.isBlank()) ? "_" : recognizerId.trim();
            gcsPrefix = normalizeGcsPrefix(gcsPrefix, "contentsummary/stt");
            pollingInterval = safeTimeout(pollingInterval, Duration.ofSeconds(2));
            pollingTimeout = safeTimeout(pollingTimeout, Duration.ofHours(2));
            model = (model == null || model.isBlank()) ? "latest_long" : model.trim();
        }

        private static String normalizeGcsPrefix(String prefix, String defaultValue) {
            if (prefix == null || prefix.isBlank()) return defaultValue;
            String trimmed = prefix.trim();
            while (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
            while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
            return trimmed.isBlank() ? defaultValue : trimmed;
        }
    }

    private static Duration safeTimeout(Duration configured, Duration fallback) {
        if (configured == null) return fallback;
        if (configured.isZero() || configured.isNegative()) return fallback;
        return configured;
    }
}

