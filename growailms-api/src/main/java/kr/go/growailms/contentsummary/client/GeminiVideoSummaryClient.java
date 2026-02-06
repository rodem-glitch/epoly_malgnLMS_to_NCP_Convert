package kr.go.growailms.contentsummary.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import kr.go.growailms.contentsummary.dto.RecoContentSummaryDraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Gemini 3 Flash를 사용하여 영상 파일에서 직접 요약을 생성하는 클라이언트.
 * 왜: STT + 전사 텍스트 요약 대신 영상을 직접 처리하면 단계가 줄고 GCS 권한이 필요 없어집니다.
 */
@Component
public class GeminiVideoSummaryClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiVideoSummaryClient.class);

    private static final String UPLOAD_BASE_URL = "https://generativelanguage.googleapis.com/upload/v1beta/files";
    private static final int CHUNK_SIZE = 8 * 1024 * 1024; // 8MB chunks for resumable upload

    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;
    private final GeminiGenerateClient geminiGenerateClient;
    private final HttpClient httpClient;

    public record UploadedFile(String fileUri, String mimeType) {
    }

    public GeminiVideoSummaryClient(GeminiProperties properties, ObjectMapper objectMapper, GeminiGenerateClient geminiGenerateClient) {
        this.properties = Objects.requireNonNull(properties);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.geminiGenerateClient = Objects.requireNonNull(geminiGenerateClient);
        // 왜: 영상 업로드는 시간이 오래 걸릴 수 있으므로 타임아웃을 넉넉하게 설정합니다.
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMinutes(5))
            .build();
    }

    /**
     * 영상 파일을 Gemini File API에 업로드하고 file URI를 반환합니다.
     */
    public UploadedFile uploadVideo(Path videoFile) throws IOException, InterruptedException {
        if (!Files.exists(videoFile)) {
            throw new IllegalArgumentException("영상 파일이 존재하지 않습니다: " + videoFile);
        }

        String apiKey = requireApiKey();
        long fileSize = Files.size(videoFile);
        String mimeType = detectMimeType(videoFile);
        String displayName = videoFile.getFileName().toString();

        log.info("Gemini File API 업로드 시작: {} ({}bytes, {})", displayName, fileSize, mimeType);

        String fileUri = null;
        try {
            // Step 1: Initiate resumable upload
            String uploadUrl = initiateResumableUpload(apiKey, fileSize, mimeType, displayName);

            // Step 2: Upload file content
            fileUri = uploadFileContent(uploadUrl, videoFile, fileSize);

            // Step 3: Wait for file to become ACTIVE
            waitForFileActive(fileUri, apiKey);

            log.info("Gemini File API 업로드 및 활성화 완료: {}", fileUri);
            return new UploadedFile(fileUri, mimeType);
        } catch (IOException | InterruptedException e) {
            // 왜: 업로드는 성공했는데(=fileUri가 생김) 이후 단계에서 실패하면,
            // Gemini File API 저장소에 파일이 남아 file_storage_bytes 쿼터를 갉아먹습니다.
            // 다음 업로드가 429로 막히지 않도록 best-effort로 정리합니다.
            deleteUploadedFileBestEffort(fileUri, apiKey);
            throw e;
        } catch (RuntimeException e) {
            deleteUploadedFileBestEffort(fileUri, apiKey);
            throw e;
        }
    }

    /**
     * Gemini File API에 업로드된 파일을 삭제합니다(best-effort).
     * 왜: 업로드 파일을 삭제하지 않으면 file_storage_bytes 쿼터가 누적되어 이후 업로드가 429로 막힐 수 있습니다.
     */
    public void deleteUploadedFileBestEffort(String fileUri) {
        String apiKey;
        try {
            apiKey = requireApiKey();
        } catch (Exception e) {
            // 요약/업로드가 이미 apiKey를 필요로 하므로 일반적으로 여기까지 오지 않지만,
            // 혹시라도 키가 없으면 삭제도 할 수 없으니 조용히 스킵합니다.
            return;
        }
        deleteUploadedFileBestEffort(fileUri, apiKey);
    }

    private void deleteUploadedFileBestEffort(String fileUri, String apiKey) {
        if (fileUri == null || fileUri.isBlank()) return;
        if (apiKey == null || apiKey.isBlank()) return;

        try {
            String trimmedUri = fileUri.trim();
            String separator = trimmedUri.contains("?") ? "&" : "?";
            URI uri = URI.create(trimmedUri + separator + "key=" + urlEncode(apiKey.trim()));

            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .DELETE()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();

            // 200/204: 정상 삭제, 404: 이미 없으면 "정리 완료"로 봅니다.
            if ((status >= 200 && status < 300) || status == 404) {
                log.info("Gemini File API 파일 삭제 완료: {} (HTTP {})", trimmedUri, status);
                return;
            }

            log.warn("Gemini File API 파일 삭제 실패: {} (HTTP {}): {}", trimmedUri, status, safeSnippet(response.body()));
        } catch (InterruptedException e) {
            // 왜: 삭제는 best-effort지만, 인터럽트는 호출자/스케줄러의 의도를 존중해야 합니다.
            Thread.currentThread().interrupt();
            log.warn("Gemini File API 파일 삭제가 인터럽트로 중단되었습니다: {} - {}", fileUri, e.getMessage());
        } catch (Exception e) {
            log.warn("Gemini File API 파일 삭제 중 예외가 발생했습니다: {} - {}", fileUri, e.getMessage());
        }
    }

    /**
     * 업로드된 영상에서 직접 요약을 생성합니다.
     */
    public RecoContentSummaryDraft summarizeFromVideo(String fileUri, String title, String mimeType, int targetSummaryLength) {
        if (fileUri == null || fileUri.isBlank()) {
            throw new IllegalArgumentException("fileUri가 비어 있습니다.");
        }

        String apiKey = requireApiKey();
        String model = properties.model();
        String safeTitle = title == null ? "" : title.trim();
        String safeMimeType = (mimeType == null || mimeType.isBlank()) ? "video/mp4" : mimeType.trim();

        URI uri = URI.create(properties.baseUrl()
            + "/models/"
            + urlEncode(model)
            + ":generateContent?key="
            + urlEncode(apiKey.trim()));

        log.info("Gemini 요약 요청 URI: {}", uri.toString().replace(apiKey, "HIDDEN_KEY"));
        // 왜: 영상 길이가 길수록 "한 문단 요약"에 담아야 할 정보가 늘어나므로,
        // 길이에 비례해 목표 글자 수를 늘릴 수 있도록 목표 길이를 파라미터로 받습니다.
        int safeTargetLength = Math.max(1, targetSummaryLength);
        boolean audioOnly = safeMimeType.toLowerCase().startsWith("audio/");
        String prompt = audioOnly
            ? buildAudioSummaryPrompt(safeTitle, safeTargetLength)
            : buildVideoSummaryPrompt(safeTitle, safeTargetLength);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(
            Map.of(
                "role", "user",
                "parts", List.of(
                    Map.of("file_data", Map.of(
                        "mime_type", safeMimeType,
                        "file_uri", fileUri
                    )),
                    Map.of("text", prompt)
                )
            )
        ));
        body.put("generationConfig", Map.of(
            "temperature", properties.temperature(),
            "maxOutputTokens", properties.maxOutputTokens()
        ));

        String jsonBody = toJson(body);

        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMinutes(10)) // 영상 분석은 시간이 더 걸릴 수 있음
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
            .build();

        String responseBody = send(request);
        String raw = extractCandidateText(responseBody);

        return parseAndNormalizeWithRepair(raw, safeTitle, safeTargetLength);
    }

    /**
     * 영상 파일을 업로드하고 바로 요약을 생성합니다 (편의 메서드).
     */
    public RecoContentSummaryDraft uploadAndSummarize(Path videoFile, String title, int targetSummaryLength) throws IOException, InterruptedException {
        UploadedFile uploaded = uploadVideo(videoFile);
        String mimeType = uploaded.mimeType();
        String fileUri = uploaded.fileUri();
        try {
            return summarizeFromVideo(fileUri, title, mimeType, targetSummaryLength);
        } finally {
            // 왜: 편의 메서드를 쓰는 호출자도 업로드 파일을 남기면 쿼터가 금방 꽉 찹니다.
            //      요약이 끝나면 업로드된 파일은 best-effort로 바로 삭제합니다.
            deleteUploadedFileBestEffort(fileUri);
        }
    }

    /**
     * 하위 호환을 위해 기본 목표 길이(300자)를 유지하는 오버로드를 남깁니다.
     */
    public RecoContentSummaryDraft uploadAndSummarize(Path videoFile, String title) throws IOException, InterruptedException {
        return uploadAndSummarize(videoFile, title, 300);
    }

    private String initiateResumableUpload(String apiKey, long fileSize, String mimeType, String displayName) 
            throws IOException, InterruptedException {
        URI uri = URI.create(UPLOAD_BASE_URL + "?key=" + urlEncode(apiKey));

        Map<String, Object> metadata = Map.of("file", Map.of("display_name", displayName));
        String metadataJson = toJson(metadata);

        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMinutes(2))
            .header("X-Goog-Upload-Protocol", "resumable")
            .header("X-Goog-Upload-Command", "start")
            .header("X-Goog-Upload-Header-Content-Length", String.valueOf(fileSize))
            .header("X-Goog-Upload-Header-Content-Type", mimeType)
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(metadataJson, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new IllegalStateException("Gemini File API 업로드 초기화 실패 (HTTP " + response.statusCode() + "): " + response.body());
        }

        // Get the upload URL from response header
        String uploadUrl = response.headers().firstValue("X-Goog-Upload-URL").orElse(null);
        if (uploadUrl == null || uploadUrl.isBlank()) {
            throw new IllegalStateException("Gemini File API 응답에 업로드 URL이 없습니다.");
        }

        return uploadUrl;
    }

    private String uploadFileContent(String uploadUrl, Path videoFile, long fileSize) 
            throws IOException, InterruptedException {
        byte[] fileBytes = Files.readAllBytes(videoFile);

        HttpRequest request = HttpRequest.newBuilder(URI.create(uploadUrl))
            .timeout(Duration.ofMinutes(30)) // 대용량 파일 업로드 타임아웃
            .header("X-Goog-Upload-Offset", "0")
            .header("X-Goog-Upload-Command", "upload, finalize")
            // Content-Length는 HttpClient가 자동 설정하므로 생략
            .POST(HttpRequest.BodyPublishers.ofByteArray(fileBytes))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new IllegalStateException("Gemini File API 파일 업로드 실패 (HTTP " + response.statusCode() + "): " + response.body());
        }

        // Parse response to get file URI
        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode fileNode = root.get("file");
            if (fileNode == null) {
                throw new IllegalStateException("응답에 file 정보가 없습니다.");
            }
            String uri = fileNode.has("uri") ? fileNode.get("uri").asText() : null;
            if (uri == null || uri.isBlank()) {
                throw new IllegalStateException("응답에 file.uri가 없습니다.");
            }
            return uri;
        } catch (Exception e) {
            throw new IllegalStateException("Gemini File API 응답 파싱 실패: " + response.body(), e);
        }
    }

    /**
     * 파일 상태가 ACTIVE가 될 때까지 폴링하며 기다립니다.
     */
    private void waitForFileActive(String fileUri, String apiKey) throws IOException, InterruptedException {
        log.info("Gemini 파일 활성화 대기 중: {}", fileUri);
        
        URI uri = URI.create(fileUri + "?key=" + urlEncode(apiKey));
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();

        long start = System.currentTimeMillis();
        long timeoutMs = 10 * 60 * 1000; // 최대 10분 대기

        while (System.currentTimeMillis() - start < timeoutMs) {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            
            if (response.statusCode() != 200) {
                log.warn("파일 상태 확인 실패 (HTTP {}): {}", response.statusCode(), response.body());
            } else {
                JsonNode root = objectMapper.readTree(response.body());
                String state = root.has("state") ? root.get("state").asText() : "UNKNOWN";

                log.info("파일 상태: {}", state);

                if ("ACTIVE".equalsIgnoreCase(state)) {
                    return;
                }
                if ("FAILED".equalsIgnoreCase(state)) {
                    throw new IllegalStateException("Gemini 파일 처리 실패: " + response.body());
                }
            }

            // 5초 대기
            Thread.sleep(5000);
        }

        throw new IllegalStateException("Gemini 파일 활성화 타임아웃 (10분 초과)");
    }

    private String buildAudioSummaryPrompt(String title, int targetSummaryLength) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 교육용 콘텐츠를 추천하기 위한 메타데이터를 생성하는 어시스턴트입니다.\n");
        sb.append("입력 파일은 '화면이 없는 오디오 기반 MP4'일 수 있으니, 음성(대사/강의 내용) 중심으로 분석하세요.\n");
        sb.append("음성이 영어여도 결과는 한국어로 작성하세요(필요하면 원어를 괄호로 병기).\n");
        sb.append("오디오를 분석하고, 반드시 JSON만 출력하세요(설명/마크다운/코드블록 금지).\n");
        sb.append("JSON 스키마\n");
        sb.append("{\n");
        sb.append("  \"category_nm\": \"기술분야 키워드 2개(쉼표로 구분)\",\n");
        sb.append("  \"summary\": \"오디오 내용 요약 약 ").append(targetSummaryLength).append("자(공백 포함, 한국어)\",\n");
        sb.append("  \"keywords\": [\"키워드\", \"키워드\", \"... 최대 10개\"]\n");
        sb.append("}\n");
        sb.append("규칙:\n");
        sb.append("- category_nm은 정확히 2개 키워드로 구성하고, 너무 일반적인 단어(예: 기술, 강의)만 쓰지 마세요.\n");
        sb.append("- summary는 1문단으로 작성하고, 가능한 한 구체적인 내용(주제/핵심 포인트/예시)을 포함하세요.\n");
        sb.append("- keywords는 중복 없이 최대 10개, 가능하면 한국어로 작성하세요.\n");
        if (!title.isBlank()) {
            sb.append("\n오디오 제목: ").append(title).append("\n");
        }
        return sb.toString();
    }

    private String buildVideoSummaryPrompt(String title, int targetSummaryLength) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 교육용 영상 콘텐츠를 추천하기 위한 메타데이터를 생성하는 도우미입니다.\n");
        sb.append("이 영상을 분석하고, 반드시 JSON만 출력하세요(설명/마크다운/코드블록 금지).\n");
        sb.append("JSON 스키마:\n");
        sb.append("{\n");
        sb.append("  \"category_nm\": \"기술분야 키워드 2개(쉼표로 구분)\",\n");
        sb.append("  \"summary\": \"영상 내용 요약 약 ").append(targetSummaryLength).append("자(공백 포함, 한글)\",\n");
        sb.append("  \"keywords\": [\"키워드1\", \"키워드2\", \"... 최대 10개\"]\n");
        sb.append("}\n");
        sb.append("규칙:\n");
        sb.append("- category_nm은 정확히 2개 키워드로 구성하고, 너무 일반적인 단어(예: 기술, 강의)는 피하세요.\n");
        sb.append("- summary는 약 ").append(targetSummaryLength).append("자(공백 포함) 1문단으로 작성하세요.\n");
        sb.append("- keywords는 영상 내용을 대표하는 키워드를 최대 10개까지, 중복 없이 작성하세요.\n");
        sb.append("- 영상의 음성과 화면 내용을 모두 참고하여 분석하세요.\n");
        if (!title.isBlank()) {
            sb.append("\n영상 제목: ").append(title).append("\n");
        }
        return sb.toString();
    }

    private RecoContentSummaryDraft parseAndNormalize(String raw) {
        JsonNode json = parseJsonFromText(raw);
        String categoryNm = text(json, "category_nm");
        String summary = text(json, "summary");
        List<String> keywords = readKeywords(json.get("keywords"));

        String normalizedCategory = normalizeCategory(categoryNm, keywords);
        List<String> normalizedKeywords = normalizeKeywords(keywords);

        return new RecoContentSummaryDraft(
            normalizedCategory,
            summary == null ? "" : summary.trim().replaceAll("\\s+", " "),
            normalizedKeywords
        );
    }

    private RecoContentSummaryDraft parseAndNormalizeWithRepair(String raw, String title, int targetSummaryLength) {
        try {
            return parseAndNormalize(raw);
        } catch (RuntimeException first) {
            RecoContentSummaryDraft fromRaw = parseLoosely(raw);
            // 왜: 멀티모달 요약 결과가 "JSON만 출력"을 지키지 않거나(따옴표/괄호 누락 등) 중간에서 끊기는 케이스가 있습니다.
            // 영상 재분석(비용↑) 대신, 모델 출력 텍스트를 1회만 정리해서 "정상 JSON"으로 다시 받아 파싱합니다.
            log.warn("Gemini 영상 요약 JSON 파싱 실패, 텍스트 재정렬 1회 시도. raw={}", safeSnippet(raw), first);
            String fixed = geminiGenerateClient.generateText(buildRepairPrompt(title, targetSummaryLength, raw));
            try {
                RecoContentSummaryDraft fromFixed = parseAndNormalize(fixed);
                return RecoContentSummaryDraftSelector.pickBetterDraft(fromRaw, fromFixed, targetSummaryLength);
            } catch (RuntimeException second) {
                // 왜: 드물게 "재정렬" 요청에서도 JSON이 깨지는 경우가 있습니다.
                // 이 때는 비용을 더 쓰기보다, 최소한의 정보(category/summary/keywords 일부)라도 뽑아서 저장해
                // 대량 처리에서 실패율을 낮추는 것이 현실적입니다.
                log.warn("Gemini 재정렬 결과도 JSON 파싱 실패, 느슨한 파싱으로 fallback 합니다. fixed={}", safeSnippet(fixed), second);
                return parseAndNormalizeLoosely(raw, fixed, targetSummaryLength);
            }
        }
    }

    private RecoContentSummaryDraft parseAndNormalizeLoosely(String raw, String fixed, int targetSummaryLength) {
        // 왜: JSON이 깨진 상태(끝이 잘림/따옴표 누락)여도, 우리가 필요한 값은 3개(category/summary/keywords)뿐입니다.
        // "완벽한 JSON"을 강제하다가 전부 실패 처리하는 것보다, 뽑을 수 있는 만큼 뽑아서 저장하는 편이 운영에 유리합니다.
        RecoContentSummaryDraft fromFixed = parseLoosely(fixed);
        RecoContentSummaryDraft fromRaw = parseLoosely(raw);

        // 왜: 재정렬 결과가 더 깔끔할 가능성이 높지만, 비어 있으면 원문에서라도 건져옵니다.
        // 왜: fixed 출력이 중간에서 잘리면(summary가 몇 글자만 남음) DB에 짧게 저장되는 문제가 생깁니다.
        // raw/fixed 중 목표 글자수 기준으로 더 안전한 값을 선택합니다.
        return RecoContentSummaryDraftSelector.pickBetterDraft(fromRaw, fromFixed, targetSummaryLength);
    }

    private RecoContentSummaryDraft parseLoosely(String raw) {
        if (raw == null || raw.isBlank()) {
            return new RecoContentSummaryDraft("기타, 기타", "", List.of());
        }

        String categoryNm = extractJsonStringValue(raw, "category_nm");
        String summary = extractJsonStringValue(raw, "summary");
        List<String> keywords = extractJsonStringArray(raw, "keywords");

        String normalizedCategory = normalizeCategory(categoryNm, keywords);
        List<String> normalizedKeywords = normalizeKeywords(keywords);

        return new RecoContentSummaryDraft(
            normalizedCategory,
            summary == null ? "" : summary.trim().replaceAll("\\s+", " "),
            normalizedKeywords
        );
    }

    private static String extractJsonStringValue(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) return null;
        if (fieldName == null || fieldName.isBlank()) return null;

        String s = raw.trim();

        // 1) 정상 JSON 케이스(따옴표 닫힘) 빠른 경로
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "\""
                + java.util.regex.Pattern.quote(fieldName)
                + "\"\\s*:\\s*\"(?<v>(?:\\\\.|[^\"\\\\])*)\"",
            java.util.regex.Pattern.DOTALL
        );
        java.util.regex.Matcher m = p.matcher(s);
        if (m.find()) {
            return unescapeJsonString(m.group("v"));
        }

        // 2) 깨진 케이스(따옴표 닫힘/괄호 닫힘 누락): "field":" ... <끝>
        int keyIdx = s.indexOf("\"" + fieldName + "\"");
        if (keyIdx < 0) return null;
        int colonIdx = s.indexOf(':', keyIdx);
        if (colonIdx < 0) return null;
        int firstQuote = s.indexOf('"', colonIdx + 1);
        if (firstQuote < 0) return null;

        int i = firstQuote + 1;
        boolean escaped = false;
        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                // 정상적으로 닫힌 경우
                String inside = s.substring(firstQuote + 1, i);
                return unescapeJsonString(inside);
            }
        }

        // 끝까지 갔는데 닫는 따옴표가 없으면, 남은 텍스트를 값으로 취급(최소한이라도 건지기)
        String tail = s.substring(firstQuote + 1);
        return unescapeJsonString(tail).trim();
    }

    private static List<String> extractJsonStringArray(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) return List.of();
        if (fieldName == null || fieldName.isBlank()) return List.of();

        String s = raw.trim();
        int keyIdx = s.indexOf("\"" + fieldName + "\"");
        if (keyIdx < 0) return List.of();
        int colonIdx = s.indexOf(':', keyIdx);
        if (colonIdx < 0) return List.of();
        int openIdx = s.indexOf('[', colonIdx);
        if (openIdx < 0) return List.of();

        int closeIdx = s.indexOf(']', openIdx + 1);
        String inside = closeIdx > openIdx ? s.substring(openIdx + 1, closeIdx) : s.substring(openIdx + 1);

        // "문자열" 토큰만 뽑습니다(중간이 잘려도 가능한 만큼).
        List<String> out = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"(?<v>(?:\\\\.|[^\"\\\\])*)\"").matcher(inside);
        while (m.find()) {
            String token = unescapeJsonString(m.group("v"));
            if (token == null) continue;
            String t = token.trim();
            if (t.isBlank()) continue;
            out.add(t);
            if (out.size() >= 10) break;
        }
        return out;
    }

    private static String unescapeJsonString(String s) {
        if (s == null) return null;
        // 왜: 정상 JSON일 때는 ObjectMapper가 해주지만, 느슨한 파싱에서는 최소한의 escape만 복원해 줍니다.
        // (복잡한 escape 전체를 완벽히 처리하려 하기보다, 실무에서 자주 나오는 케이스만 처리)
        return s
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
    }

    private static String buildRepairPrompt(String title, int targetSummaryLength, String raw) {
        String safeTitle = title == null ? "" : title.trim();
        int safeTarget = Math.max(1, targetSummaryLength);
        String s = raw == null ? "" : raw.trim();

        // 왜: 깨진 JSON이 너무 길면(불필요한 설명/중복 포함) 입력 토큰을 낭비하므로 적당히 잘라서 보냅니다.
        int maxLen = 8000;
        if (s.length() > maxLen) {
            s = s.substring(0, maxLen);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("아래 텍스트는 JSON만 출력해야 하는데 깨진 출력입니다.\n");
        sb.append("반드시 JSON만, 아래 스키마대로 완성해서 출력해 주세요(설명/마크다운/코드블록 금지).\n");
        sb.append("JSON 스키마\n");
        sb.append("{\n");
        sb.append("  \"category_nm\": \"기술분야 키워드 2개(쉼표로 구분)\",\n");
        sb.append("  \"summary\": \"영상 내용 요약 약 ").append(safeTarget).append("자(공백 포함), 1문단\",\n");
        sb.append("  \"keywords\": [\"키워드\", \"키워드\", \"... 최대 10개\"]\n");
        sb.append("}\n");
        sb.append("규칙\n");
        sb.append("- category_nm은 정확히 2개 키워드만 포함하고, 너무 일반적인 단어는 피하세요.\n");
        sb.append("- summary는 약 ").append(safeTarget).append("자(공백 포함)로 맞춰주세요.\n");
        sb.append("- keywords는 중복 없이 최대 10개.\n");
        if (!safeTitle.isBlank()) {
            sb.append("\n영상 제목: ").append(safeTitle).append("\n");
        }
        sb.append("\n원문 텍스트(이 내용을 기준으로 JSON을 완성):\n");
        sb.append(s);
        return sb.toString();
    }

    private JsonNode parseJsonFromText(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("```")) {
            s = s.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("\\s*```$", "").trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            s = s.substring(start, end + 1);
        }
        try {
            return objectMapper.readTree(s);
        } catch (Exception e) {
            throw new IllegalStateException("요약 JSON 파싱에 실패했습니다. 원문=" + safeSnippet(raw), e);
        }
    }

    private String normalizeCategory(String categoryNm, List<String> keywords) {
        List<String> parts = splitTokens(categoryNm);
        if (parts.size() >= 2) {
            return parts.get(0) + ", " + parts.get(1);
        }
        if (parts.size() == 1) {
            String second = keywords != null && !keywords.isEmpty() ? keywords.get(0) : "기타";
            if (second.equals(parts.get(0))) second = "기타";
            return parts.get(0) + ", " + second;
        }
        if (keywords != null && keywords.size() >= 2) {
            return keywords.get(0) + ", " + keywords.get(1);
        }
        if (keywords != null && keywords.size() == 1) {
            return keywords.get(0) + ", 기타";
        }
        return "기타, 기타";
    }

    private List<String> normalizeKeywords(List<String> keywords) {
        List<String> out = new ArrayList<>();
        if (keywords != null) {
            for (String k : keywords) {
                if (k == null) continue;
                String t = k.trim();
                if (t.isBlank()) continue;
                if (!out.contains(t)) out.add(t);
                if (out.size() >= 10) break;
            }
        }
        return out;
    }

    private static List<String> splitTokens(String raw) {
        if (raw == null) return List.of();
        String s = raw.trim();
        if (s.isBlank()) return List.of();
        String normalized = s.replace("|", ",").replace("/", ",").replace("·", ",");
        String[] arr = normalized.split(",");
        List<String> out = new ArrayList<>();
        for (String a : arr) {
            if (a == null) continue;
            String t = a.trim();
            if (t.isBlank()) continue;
            out.add(t);
        }
        return out;
    }

    private static String text(JsonNode root, String field) {
        if (root == null) return null;
        JsonNode v = root.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText(null);
        return s == null ? null : s.trim();
    }

    private List<String> readKeywords(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return List.of();
        if (node.isArray()) {
            List<String> out = new ArrayList<>();
            for (JsonNode n : node) {
                if (n == null || n.isNull()) continue;
                String s = n.asText(null);
                if (s == null || s.isBlank()) continue;
                out.add(s.trim());
                if (out.size() >= 10) break;
            }
            return out;
        }
        String s = node.asText("");
        if (s.isBlank()) return List.of();
        String normalized = s.replace("\n", ",");
        String[] arr = normalized.split(",");
        List<String> out = new ArrayList<>();
        for (String a : arr) {
            String t = a == null ? "" : a.trim();
            if (t.isBlank()) continue;
            out.add(t);
            if (out.size() >= 10) break;
        }
        return out;
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Gemini API 호출 실패 (HTTP " + status + "): " + safeSnippet(response.body()));
            }
            return response.body();
        } catch (Exception e) {
            throw new IllegalStateException("Gemini API 통신 중 오류가 발생했습니다.", e);
        }
    }

    private String extractCandidateText(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            // 왜: Gemini 응답이 parts 여러 개로 쪼개져 오는 경우가 있어(특히 멀티모달),
            // parts[0]만 읽으면 JSON이 중간에서 끊겨 파싱에 실패할 수 있습니다.
            JsonNode parts = root.at("/candidates/0/content/parts");
            if (parts.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode part : parts) {
                    if (part == null || part.isNull()) continue;
                    JsonNode text = part.get("text");
                    if (text == null || text.isNull()) continue;
                    sb.append(text.asText(""));
                }
                return sb.toString().trim();
            }

            JsonNode text = root.at("/candidates/0/content/parts/0/text");
            if (text.isMissingNode() || text.isNull()) return "";
            return text.asText("").trim();
        } catch (Exception e) {
            throw new IllegalStateException("Gemini 응답 파싱에 실패했습니다.", e);
        }
    }

    private String requireApiKey() {
        String apiKey = properties.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("gemini.api-key 설정이 필요합니다.");
        }
        return apiKey.trim();
    }

    private String detectMimeType(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".mp4")) {
            // 왜: mp4 확장자라도 video 트랙이 없는 "오디오-only mp4"가 존재합니다.
            //     이런 파일을 video/mp4로 업로드하면 Gemini File API 활성화 단계에서 실패/타임아웃이 날 수 있어 audio/mp4로 분기합니다.
            Mp4TrackInspector.TrackTypes tracks = Mp4TrackInspector.inspect(file);
            if (!tracks.hasVideo() && tracks.hasAudio()) return "audio/mp4";
            return "video/mp4";
        }
        if (fileName.endsWith(".mpeg") || fileName.endsWith(".mpg")) return "video/mpeg";
        if (fileName.endsWith(".mov")) return "video/quicktime";
        if (fileName.endsWith(".avi")) return "video/x-msvideo";
        if (fileName.endsWith(".webm")) return "video/webm";
        if (fileName.endsWith(".mkv")) return "video/x-matroska";
        // 기본값은 mp4
        return "video/mp4";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 생성에 실패했습니다.", e);
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String safeSnippet(String raw) {
        if (raw == null) return "";
        String s = raw.replace("\r", " ").replace("\n", " ").trim();
        if (s.length() <= 500) return s;
        return s.substring(0, 500) + "...";
    }
}
