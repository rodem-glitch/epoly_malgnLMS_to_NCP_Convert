package kr.go.growailms.contentsummary.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 왜: 다운로드는 네트워크/용량/중간 끊김 변수가 많아서, "API 호출"과 분리해두는 게 디버깅/재시도에 유리합니다.
 */
@Component
public class KollusMediaDownloader {

    private final HttpClient httpClient;
    private final KollusProperties properties;

    public KollusMediaDownloader(KollusProperties properties) {
        this.properties = Objects.requireNonNull(properties);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.httpTimeout())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public Path downloadTo(URI downloadUri, Path targetFile) {
        try {
            Files.createDirectories(targetFile.getParent());
            HttpRequest request = HttpRequest.newBuilder(downloadUri)
                .timeout(adjustTimeout(properties.httpTimeout(), Duration.ofMinutes(30)))
                .GET()
                .build();

            HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(targetFile));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Kollus 미디어 다운로드 실패 (HTTP " + status + ")");
            }
            return response.body();
        } catch (Exception e) {
            throw new IllegalStateException("Kollus 미디어 다운로드 중 오류가 발생했습니다.", e);
        }
    }

    private static Duration adjustTimeout(Duration base, Duration minimum) {
        if (base == null) return minimum;
        return base.compareTo(minimum) >= 0 ? base : minimum;
    }
}

