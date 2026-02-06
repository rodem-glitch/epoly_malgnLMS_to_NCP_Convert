package kr.go.growailms.contentsummary.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import kr.go.growailms.contentsummary.client.GeminiGenerateClient;
import kr.go.growailms.contentsummary.dto.RecoContentSummaryDraft;
import org.springframework.stereotype.Service;

@Service
public class RecoContentSummaryGenerator {

    private final GeminiGenerateClient geminiGenerateClient;
    private final ObjectMapper objectMapper;

    public RecoContentSummaryGenerator(GeminiGenerateClient geminiGenerateClient, ObjectMapper objectMapper) {
        // 왜: 요약/키워드는 "모델 출력"이 100% 고정되지 않으므로, 생성과 파싱/정규화를 한 곳에서 책임지게 합니다.
        this.geminiGenerateClient = Objects.requireNonNull(geminiGenerateClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public RecoContentSummaryDraft generate(String title, String transcriptText) {
        String safeTitle = title == null ? "" : title.trim();
        String safeTranscript = transcriptText == null ? "" : transcriptText.trim();
        if (safeTranscript.isBlank()) {
            throw new IllegalArgumentException("전사 텍스트가 비어 있어 요약을 생성할 수 없습니다.");
        }

        String prompt = buildPrompt(safeTitle, shrinkTranscript(safeTranscript));
        String raw = geminiGenerateClient.generateText(prompt);

        RecoContentSummaryDraft draft = parseAndNormalize(raw);
        if (!isSummaryLengthOk(draft.summary())) {
            // 왜: 200~300자 조건이 중요해서 1회만 보정 요청을 추가로 합니다(무한 재시도 방지).
            String fixPrompt = buildFixPrompt(safeTitle, draft, shrinkTranscript(safeTranscript));
            String fixedRaw = geminiGenerateClient.generateText(fixPrompt);
            draft = parseAndNormalize(fixedRaw);
        }
        return normalizeLengths(draft);
    }

    private String buildPrompt(String title, String transcriptText) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 교육용 영상 콘텐츠를 추천하기 위한 메타데이터를 생성하는 도우미입니다.\n");
        sb.append("아래 전사 텍스트를 읽고, 반드시 JSON만 출력하세요(설명/마크다운/코드블록 금지).\n");
        sb.append("JSON 스키마:\n");
        sb.append("{\n");
        sb.append("  \"category_nm\": \"기술분야 키워드 2개(쉼표로 구분)\",\n");
        sb.append("  \"summary\": \"전사 텍스트 요약 200~300자(공백 포함, 한글)\",\n");
        sb.append("  \"keywords\": [\"키워드1\", \"키워드2\", \"... 최대 10개\"]\n");
        sb.append("}\n");
        sb.append("규칙:\n");
        sb.append("- category_nm은 정확히 2개 키워드로 구성하고, 너무 일반적인 단어(예: 기술, 강의)는 피하세요.\n");
        sb.append("- summary는 200~300자(공백 포함) 1문단으로 작성하세요.\n");
        sb.append("- keywords는 영상 내용을 대표하는 키워드를 최대 10개까지, 중복 없이 작성하세요.\n");
        sb.append("\n");
        if (!title.isBlank()) {
            sb.append("영상 제목: ").append(title).append("\n");
        }
        sb.append("전사 텍스트:\n");
        sb.append(transcriptText);
        return sb.toString();
    }

    private String buildFixPrompt(String title, RecoContentSummaryDraft draft, String transcriptText) {
        StringBuilder sb = new StringBuilder();
        sb.append("아래 JSON을 참고해서 summary 길이만 200~300자(공백 포함)로 다시 작성해 주세요.\n");
        sb.append("반드시 JSON만 출력하세요(설명/마크다운/코드블록 금지).\n");
        sb.append("category_nm, keywords는 최대한 그대로 유지하세요.\n\n");
        sb.append("현재 JSON:\n");
        sb.append(toJson(draft)).append("\n\n");
        if (!title.isBlank()) {
            sb.append("영상 제목: ").append(title).append("\n");
        }
        sb.append("전사 텍스트:\n");
        sb.append(transcriptText);
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
        Set<String> out = new LinkedHashSet<>();
        if (keywords != null) {
            for (String k : keywords) {
                if (k == null) continue;
                String t = k.trim();
                if (t.isBlank()) continue;
                out.add(t);
                if (out.size() >= 10) break;
            }
        }
        return new ArrayList<>(out);
    }

    private static boolean isSummaryLengthOk(String summary) {
        if (summary == null) return false;
        String s = summary.trim();
        if (s.isBlank()) return false;
        int len = s.codePointCount(0, s.length());
        // ✅ 요약 길이는 호출부에서 "목표 글자 수"로 제어하고, 여기서는 비어있지만 않으면 통과시킵니다.
        return len > 0;
    }

    private RecoContentSummaryDraft normalizeLengths(RecoContentSummaryDraft draft) {
        String category = trimToCodePoints(draft.categoryNm(), 100);
        // ✅ 더 이상 요약을 300자로 자르지 않습니다. (긴 영상/긴 텍스트 요약이 잘리는 문제 방지)
        String summary = draft.summary();
        if (summary != null) summary = summary.trim();

        List<String> keywords = draft.keywords() == null ? List.of() : draft.keywords();
        List<String> limitedKeywords = new ArrayList<>();
        for (String k : keywords) {
            if (k == null) continue;
            String t = k.trim();
            if (t.isBlank()) continue;
            limitedKeywords.add(trimToCodePoints(t, 50));
            if (limitedKeywords.size() >= 10) break;
        }

        return new RecoContentSummaryDraft(category, summary, limitedKeywords);
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
        // 쉼표/줄바꿈으로 넘어온 경우도 대비
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

    private static String trimToCodePoints(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isBlank()) return "";
        int len = t.codePointCount(0, t.length());
        if (len <= max) return t;
        int endIndex = t.offsetByCodePoints(0, max);
        return t.substring(0, endIndex);
    }

    private static String safeSnippet(String raw) {
        if (raw == null) return "";
        String s = raw.replace("\r", " ").replace("\n", " ").trim();
        if (s.length() <= 300) return s;
        return s.substring(0, 300) + "...";
    }

    private String toJson(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String shrinkTranscript(String transcriptText) {
        // 왜: 전사 텍스트가 아주 길면 모델 호출이 느려지고 실패 가능성이 커집니다.
        // 추천용 요약은 대략적인 내용이 중요하므로, 앞/뒤를 샘플링해서 길이를 제한합니다.
        int max = 50_000;
        String s = transcriptText == null ? "" : transcriptText;
        if (s.length() <= max) return s;
        int head = 30_000;
        int tail = 15_000;
        String h = s.substring(0, Math.min(head, s.length()));
        String t = s.substring(Math.max(0, s.length() - tail));
        return h + "\n...\n" + t;
    }
}

