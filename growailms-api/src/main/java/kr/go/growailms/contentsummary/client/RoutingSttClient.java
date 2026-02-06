package kr.go.growailms.contentsummary.client;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 왜: STT 제공자를 설정으로 바꿔가며 실험해야 해서,
 * 서비스 코드(ContentSummaryService)는 "전사해줘"만 호출하고 실제 구현은 여기서 라우팅합니다.
 *
 * - 현재는 Google Speech-to-Text(v2)만 사용합니다.
 * - 어떤 STT를 쓸지는 `stt.provider`로 결정합니다(향후 확장 대비).
 *
 * 주의: 이 클라이언트는 stt.enabled=true인 경우에만 로드됩니다.
 */
@Primary
@Component
@ConditionalOnProperty(name = "stt.enabled", havingValue = "true", matchIfMissing = false)
public class RoutingSttClient implements SttClient {

    private final SttProperties properties;
    private final GoogleSpeechV2SttClient googleSpeechV2SttClient;

    public RoutingSttClient(
        SttProperties properties,
        GoogleSpeechV2SttClient googleSpeechV2SttClient
    ) {
        this.properties = Objects.requireNonNull(properties);
        this.googleSpeechV2SttClient = Objects.requireNonNull(googleSpeechV2SttClient);
    }

    @Override
    public String transcribe(Path mediaFile, String language) {
        String provider = normalize(properties.provider());
        if (provider.isBlank()) provider = "google";
        if ("google".equals(provider) || "gcp".equals(provider) || "gcp-speech-v2".equals(provider)) {
            return googleSpeechV2SttClient.transcribe(mediaFile, language);
        }
        throw new IllegalStateException("지원하지 않는 STT provider 입니다: " + provider + " (google/gcp/gcp-speech-v2)");
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
