package kr.go.growailms.contentsummary.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Google Cloud Speech-to-Text v2(STT) 구현체입니다.
 *
 * 왜: 긴 영상(수십 분~수 시간)은 요청 본문에 음성 데이터를 그대로 넣는 방식이 제한/실패가 잦습니다.
 * 그래서 (1) 오디오를 GCS에 올리고 (2) Speech-to-Text v2의 batchRecognize를 호출해 (3) Operation 완료까지 폴링합니다.
 *
 * 전제:
 * - 인증: 서비스계정 JSON 파일 경로를 `GOOGLE_APPLICATION_CREDENTIALS` 환경변수로 지정합니다.
 * - 권한: Speech-to-Text 사용 권한 + GCS 버킷 Object 접근 권한이 필요합니다.
 *
 * 주의: 이 클라이언트는 stt.enabled=true인 경우에만 로드됩니다.
 * Gemini 영상 직접 처리 방식을 사용하는 경우 이 빈은 로드되지 않습니다.
 */
@Component
@ConditionalOnProperty(name = "stt.enabled", havingValue = "true", matchIfMissing = false)
public class GoogleSpeechV2SttClient implements SttClient {

    private static final URI SPEECH_API_BASE = URI.create("https://speech.googleapis.com/v2/");
    private static final List<String> GOOGLE_OAUTH_SCOPES = List.of("https://www.googleapis.com/auth/cloud-platform");
    private static final DateTimeFormatter DATE_PREFIX = DateTimeFormatter.BASIC_ISO_DATE;

    private final SttProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final GoogleCredentials credentials;
    private final Storage storage;

    public GoogleSpeechV2SttClient(SttProperties properties, ObjectMapper objectMapper) {
        this.properties = Objects.requireNonNull(properties);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();
        this.credentials = loadCredentials();
        this.storage = StorageOptions.newBuilder()
            .setCredentials(credentials)
            .build()
            .getService();
    }

    @Override
    public String transcribe(Path mediaFile, String language) {
        SttProperties.Google google = properties.google();
        ensureConfigured(google.projectId(), "stt.google.project-id");
        ensureConfigured(google.gcsBucket(), "stt.google.gcs-bucket");

        if (mediaFile == null) throw new IllegalArgumentException("mediaFile이 null 입니다.");
        if (!Files.exists(mediaFile)) throw new IllegalArgumentException("mediaFile이 존재하지 않습니다: " + mediaFile);

        String lang = normalizeLanguageCode(firstNonBlank(language, properties.language()));
        UploadedObject uploaded = uploadToGcs(mediaFile, google);

        boolean keepFile = google.keepUploadedFiles();
        try {
            String operationName = startBatchRecognize(google, uploaded.gcsUri(), lang, isWav(mediaFile));
            JsonNode operation = waitOperationDone(operationName, google);
            JsonNode response = operation == null ? null : operation.get("response");
            return extractTranscriptFromBatchRecognizeResponse(response);
        } catch (TimeoutException e) {
            // 왜: operation이 계속 진행 중일 수 있어, 타임아웃일 때는 업로드 파일을 삭제하지 않습니다(디버깅/재처리 가능).
            keepFile = true;
            throw e;
        } finally {
            if (!keepFile) safeDeleteGcsObject(uploaded);
        }
    }

    private GoogleCredentials loadCredentials() {
        try {
            GoogleCredentials c = GoogleCredentials.getApplicationDefault();
            if (c.createScopedRequired()) c = c.createScoped(GOOGLE_OAUTH_SCOPES);
            return c;
        } catch (Exception e) {
            throw new IllegalStateException(
                "Google Cloud 인증정보를 불러오지 못했습니다. GOOGLE_APPLICATION_CREDENTIALS(서비스계정 JSON 경로)를 확인해 주세요.",
                e
            );
        }
    }

    private synchronized String accessToken() {
        try {
            credentials.refreshIfExpired();
            AccessToken token = credentials.getAccessToken();
            if (token == null || token.getTokenValue() == null || token.getTokenValue().isBlank()) {
                token = credentials.refreshAccessToken();
            }
            String v = token == null ? null : token.getTokenValue();
            if (v == null || v.isBlank()) {
                throw new IllegalStateException("Google OAuth 토큰 값이 비어 있습니다.");
            }
            return v.trim();
        } catch (Exception e) {
            throw new IllegalStateException("Google OAuth 토큰 발급에 실패했습니다. 서비스계정/권한/네트워크를 확인해 주세요.", e);
        }
    }

    private UploadedObject uploadToGcs(Path mediaFile, SttProperties.Google google) {
        String bucket = google.gcsBucket().trim();
        String prefix = google.gcsPrefix();

        String ext = fileExtension(mediaFile);
        String objectName = prefix
            + "/" + DATE_PREFIX.format(LocalDate.now())
            + "/" + UUID.randomUUID().toString().replace("-", "")
            + (ext.isBlank() ? "" : "." + ext);

        String contentType = guessContentType(mediaFile);

        BlobId blobId = BlobId.of(bucket, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
            .setContentType(contentType)
            .build();

        try (WriteChannel writer = storage.writer(blobInfo);
             InputStream in = Files.newInputStream(mediaFile)) {
            // 왜: 영상/오디오는 크기가 클 수 있어서 메모리에 전부 올리지 않고 스트리밍으로 업로드합니다.
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read == 0) continue;
                writer.write(ByteBuffer.wrap(buffer, 0, read));
            }
        } catch (Exception e) {
            throw new IllegalStateException("GCS 업로드에 실패했습니다. 버킷 권한/네트워크를 확인해 주세요.", e);
        }

        String gcsUri = "gs://" + bucket + "/" + objectName;
        return new UploadedObject(bucket, objectName, gcsUri);
    }

    private String startBatchRecognize(SttProperties.Google google, String gcsUri, String languageCode, boolean isWav) {
        String recognizer = buildRecognizerName(google);
        URI uri = URI.create(SPEECH_API_BASE.toString() + recognizer + ":batchRecognize");

        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode config = body.putObject("config");
        ArrayNode languages = config.putArray("languageCodes");
        languages.add(languageCode);
        config.put("model", google.model());

        ObjectNode features = config.putObject("features");
        features.put("enableAutomaticPunctuation", google.enableAutomaticPunctuation());

        // 왜: wav(16kHz/mono)로 뽑힌 경우엔 명시적으로 알려주면 인식 안정성이 좋아집니다.
        // 그 외(mp4 등)는 autoDecodingConfig로 맡깁니다.
        if (isWav) {
            ObjectNode decoding = config.putObject("explicitDecodingConfig");
            decoding.put("encoding", "LINEAR16");
            decoding.put("sampleRateHertz", 16000);
            decoding.put("audioChannelCount", 1);
        } else {
            config.putObject("autoDecodingConfig");
        }

        ArrayNode files = body.putArray("files");
        files.addObject().put("uri", gcsUri);

        // 왜: 1개 파일만 넣을 것이므로 inline 응답으로 받아서 DB에 바로 저장할 수 있게 합니다.
        body.putObject("recognitionOutputConfig").putObject("inlineResponseConfig");

        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(properties.httpTimeout())
            .header("Authorization", "Bearer " + accessToken())
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
            .build();

        String responseBody = send(request);
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String name = root.path("name").asText(null);
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("Operation name 응답이 비어 있습니다.");
            }
            return name.trim();
        } catch (Exception e) {
            throw new IllegalStateException("batchRecognize 응답 파싱에 실패했습니다.", e);
        }
    }

    private JsonNode waitOperationDone(String operationName, SttProperties.Google google) {
        String name = operationName == null ? "" : operationName.trim();
        if (name.startsWith("/")) name = name.substring(1);

        Duration timeout = google.pollingTimeout();
        long deadlineNanos = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadlineNanos) {
            JsonNode op = getOperation(name);
            boolean done = op.path("done").asBoolean(false);
            if (!done) {
                sleep(google.pollingInterval());
                continue;
            }

            JsonNode error = op.get("error");
            if (error != null && !error.isNull() && !error.isMissingNode()) {
                int code = error.path("code").asInt(0);
                String message = error.path("message").asText("");
                throw new IllegalStateException("Google STT batchRecognize 실패(code=" + code + "): " + message);
            }

            return op;
        }

        throw new TimeoutException("Google STT batchRecognize가 타임아웃 되었습니다. (timeout=" + timeout + ")");
    }

    private JsonNode getOperation(String operationName) {
        URI uri = URI.create(SPEECH_API_BASE.toString() + operationName);
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(properties.httpTimeout())
            .header("Authorization", "Bearer " + accessToken())
            .GET()
            .build();

        String responseBody = send(request);
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new IllegalStateException("Operation 응답 파싱에 실패했습니다.", e);
        }
    }

    private String extractTranscriptFromBatchRecognizeResponse(JsonNode response) {
        if (response == null || response.isNull() || response.isMissingNode()) return "";

        StringBuilder out = new StringBuilder();
        JsonNode results = response.get("results");
        if (results != null && results.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = results.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                JsonNode fileResult = e.getValue();
                appendTranscript(out, fileResult);
            }
        } else {
            appendTranscript(out, response);
        }

        String text = out.toString().trim();
        // 왜: 후속 요약 단계에서 토큰 낭비를 줄이기 위해 과도한 공백을 정리합니다.
        return text.replaceAll("\\s+", " ");
    }

    private static void appendTranscript(StringBuilder out, JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return;

        // inline 응답 구조: { inlineResult: { transcript: { results:[{ alternatives:[{ transcript:"..." }]}] } } }
        JsonNode inlineResult = node.get("inlineResult");
        JsonNode transcriptRoot = inlineResult == null ? null : inlineResult.get("transcript");
        JsonNode results = transcriptRoot == null ? null : transcriptRoot.get("results");

        if (results != null && results.isArray()) {
            for (JsonNode r : results) {
                String t = r.at("/alternatives/0/transcript").asText(null);
                if (t == null || t.isBlank()) continue;
                if (out.length() > 0) out.append('\n');
                out.append(t.trim());
            }
            return;
        }

        // 혹시 구조가 달라도 transcript 문자열을 최대한 건져옵니다.
        String fallback = findAnyTranscriptText(node);
        if (fallback != null && !fallback.isBlank()) {
            if (out.length() > 0) out.append('\n');
            out.append(fallback.trim());
        }
    }

    private static String findAnyTranscriptText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        if (node.isObject()) {
            JsonNode direct = node.get("transcript");
            if (direct != null && direct.isTextual()) {
                String s = direct.asText();
                if (s != null && !s.isBlank()) return s;
            }
            Iterator<JsonNode> it = node.elements();
            while (it.hasNext()) {
                String found = findAnyTranscriptText(it.next());
                if (found != null) return found;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String found = findAnyTranscriptText(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                String body = response.body() == null ? "" : response.body();
                if (body.length() > 500) body = body.substring(0, 500) + "...";
                throw new IllegalStateException("Google Speech API 호출 실패 (HTTP " + status + "): " + body);
            }
            return response.body();
        } catch (Exception e) {
            throw new IllegalStateException("Google Speech API 통신 중 오류가 발생했습니다.", e);
        }
    }

    private static String buildRecognizerName(SttProperties.Google google) {
        String projectId = google.projectId() == null ? "" : google.projectId().trim();
        String location = google.location() == null ? "global" : google.location().trim();
        String recognizerId = google.recognizerId() == null ? "_" : google.recognizerId().trim();
        return "projects/" + projectId + "/locations/" + location + "/recognizers/" + recognizerId;
    }

    private static boolean isWav(Path path) {
        if (path == null) return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".wav");
    }

    private static String fileExtension(Path path) {
        if (path == null) return "";
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String guessContentType(Path path) {
        String name = path == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".wav")) return "audio/wav";
        if (name.endsWith(".mp3")) return "audio/mpeg";
        if (name.endsWith(".m4a")) return "audio/mp4";
        if (name.endsWith(".mp4")) return "video/mp4";
        return "application/octet-stream";
    }

    private static String normalizeLanguageCode(String language) {
        String t = language == null ? "" : language.trim();
        if (t.isBlank()) return "ko-KR";
        if ("ko".equalsIgnoreCase(t)) return "ko-KR";
        if ("en".equalsIgnoreCase(t)) return "en-US";
        return t;
    }

    private static void ensureConfigured(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " 설정이 필요합니다. (application-local.yml 또는 환경변수로 주입해 주세요)");
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v == null) continue;
            String t = v.trim();
            if (!t.isBlank()) return t;
        }
        return null;
    }

    private static void sleep(Duration d) {
        long ms = d == null ? 0L : d.toMillis();
        if (ms <= 0L) ms = 500L;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void safeDeleteGcsObject(UploadedObject uploaded) {
        if (uploaded == null) return;
        try {
            storage.delete(BlobId.of(uploaded.bucket(), uploaded.objectName()));
        } catch (Exception ignored) {
        }
    }

    private record UploadedObject(String bucket, String objectName, String gcsUri) {
    }

    private static class TimeoutException extends RuntimeException {
        public TimeoutException(String message) {
            super(message);
        }
    }
}

