package kr.go.growailms.security.xss;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KISA SR1-3: 크로스 사이트 스크립트(XSS) 방지
 *
 * HTML 콘텐츠 정제 유틸리티
 * - 화이트리스트 기반 태그 필터링
 * - 위험 속성 제거
 * - 악성 URL 스킴 차단
 */
public final class HtmlSanitizer {

    private static final Set<String> DEFAULT_ALLOWED_TAGS = Set.of(
            "p", "br", "b", "i", "u", "strong", "em", "span", "div",
            "ul", "ol", "li", "a", "img", "table", "thead", "tbody",
            "tr", "th", "td", "h1", "h2", "h3", "h4", "h5", "h6",
            "blockquote", "pre", "code", "hr"
    );

    private static final Map<String, Set<String>> DEFAULT_ALLOWED_ATTRIBUTES = Map.of(
            "a", Set.of("href", "title", "target", "rel"),
            "img", Set.of("src", "alt", "width", "height"),
            "span", Set.of("class", "style"),
            "div", Set.of("class", "style"),
            "table", Set.of("class", "border", "cellpadding", "cellspacing"),
            "td", Set.of("colspan", "rowspan", "class"),
            "th", Set.of("colspan", "rowspan", "class")
    );

    private static final Set<String> DANGEROUS_URL_SCHEMES = Set.of(
            "javascript", "vbscript", "data", "blob"
    );

    private static final Pattern TAG_PATTERN = Pattern.compile(
            "<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)(/?)>",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile(
            "([a-zA-Z][a-zA-Z0-9-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern STYLE_EXPRESSION_PATTERN = Pattern.compile(
            "expression\\s*\\(|javascript\\s*:|behavior\\s*:",
            Pattern.CASE_INSENSITIVE
    );

    private final Set<String> allowedTags;
    private final Map<String, Set<String>> allowedAttributes;
    private final boolean stripComments;
    private final boolean encodeEntities;

    private HtmlSanitizer(Builder builder) {
        this.allowedTags = builder.allowedTags;
        this.allowedAttributes = builder.allowedAttributes;
        this.stripComments = builder.stripComments;
        this.encodeEntities = builder.encodeEntities;
    }

    /**
     * 기본 설정으로 HTML 정제
     */
    public static String sanitize(String html) {
        return builder().build().clean(html);
    }

    /**
     * 모든 HTML 태그 제거 (텍스트만 추출)
     */
    public static String stripAllTags(String html) {
        if (html == null) {
            return null;
        }
        return html.replaceAll("<[^>]*>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 빌더 생성
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * HTML 정제 수행
     */
    public String clean(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }

        String result = html;

        // HTML 주석 제거
        if (stripComments) {
            result = removeComments(result);
        }

        // 태그 필터링
        result = filterTags(result);

        return result;
    }

    private String removeComments(String html) {
        return html.replaceAll("<!--[\\s\\S]*?-->", "");
    }

    private String filterTags(String html) {
        StringBuilder result = new StringBuilder(html.length());
        Matcher matcher = TAG_PATTERN.matcher(html);
        int lastEnd = 0;

        while (matcher.find()) {
            // 태그 이전 텍스트 추가
            result.append(processText(html.substring(lastEnd, matcher.start())));

            String slash = matcher.group(1);
            String tagName = matcher.group(2).toLowerCase();
            String attributes = matcher.group(3);
            String selfClose = matcher.group(4);

            if (allowedTags.contains(tagName)) {
                // 허용된 태그 - 속성 필터링 후 유지
                result.append("<").append(slash).append(tagName);

                if (!slash.equals("/") && attributes != null && !attributes.isEmpty()) {
                    String filteredAttrs = filterAttributes(tagName, attributes);
                    if (!filteredAttrs.isEmpty()) {
                        result.append(" ").append(filteredAttrs);
                    }
                }

                if (!selfClose.isEmpty()) {
                    result.append(" /");
                }
                result.append(">");
            }
            // 허용되지 않은 태그는 제거 (내용은 유지)

            lastEnd = matcher.end();
        }

        // 마지막 태그 이후 텍스트 추가
        if (lastEnd < html.length()) {
            result.append(processText(html.substring(lastEnd)));
        }

        return result.toString();
    }

    private String filterAttributes(String tagName, String attributes) {
        Set<String> allowed = allowedAttributes.getOrDefault(tagName, Set.of());
        if (allowed.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        Matcher matcher = ATTRIBUTE_PATTERN.matcher(attributes);

        while (matcher.find()) {
            String attrName = matcher.group(1).toLowerCase();
            String attrValue = matcher.group(2) != null ? matcher.group(2) :
                    matcher.group(3) != null ? matcher.group(3) :
                            matcher.group(4);

            if (allowed.contains(attrName)) {
                String sanitizedValue = sanitizeAttributeValue(attrName, attrValue);
                if (sanitizedValue != null) {
                    if (result.length() > 0) {
                        result.append(" ");
                    }
                    result.append(attrName).append("=\"")
                            .append(escapeAttributeValue(sanitizedValue))
                            .append("\"");
                }
            }
        }

        return result.toString();
    }

    private String sanitizeAttributeValue(String attrName, String value) {
        if (value == null) {
            return null;
        }

        // URL 속성 검사
        if ("href".equals(attrName) || "src".equals(attrName)) {
            if (isDangerousUrl(value)) {
                return null;
            }
        }

        // style 속성 검사
        if ("style".equals(attrName)) {
            if (STYLE_EXPRESSION_PATTERN.matcher(value).find()) {
                return null;
            }
        }

        return value;
    }

    private boolean isDangerousUrl(String url) {
        if (url == null) {
            return false;
        }
        String normalized = url.trim().toLowerCase().replaceAll("\\s", "");
        for (String scheme : DANGEROUS_URL_SCHEMES) {
            if (normalized.startsWith(scheme + ":")) {
                return true;
            }
        }
        return false;
    }

    private String escapeAttributeValue(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    private String processText(String text) {
        if (encodeEntities) {
            return text.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
        }
        return text;
    }

    /**
     * HtmlSanitizer 빌더
     */
    public static class Builder {
        private Set<String> allowedTags = DEFAULT_ALLOWED_TAGS;
        private Map<String, Set<String>> allowedAttributes = DEFAULT_ALLOWED_ATTRIBUTES;
        private boolean stripComments = true;
        private boolean encodeEntities = false;

        /**
         * 허용할 태그 설정
         */
        public Builder allowedTags(Set<String> tags) {
            this.allowedTags = tags;
            return this;
        }

        /**
         * 허용할 속성 설정
         */
        public Builder allowedAttributes(Map<String, Set<String>> attributes) {
            this.allowedAttributes = attributes;
            return this;
        }

        /**
         * 최소한의 태그만 허용 (b, i, u, p, br)
         */
        public Builder minimal() {
            this.allowedTags = Set.of("b", "i", "u", "p", "br", "strong", "em");
            this.allowedAttributes = Map.of();
            return this;
        }

        /**
         * 텍스트 서식 태그만 허용
         */
        public Builder textFormatting() {
            this.allowedTags = Set.of("p", "br", "b", "i", "u", "strong", "em",
                    "span", "h1", "h2", "h3", "h4", "h5", "h6");
            this.allowedAttributes = Map.of("span", Set.of("class"));
            return this;
        }

        /**
         * HTML 주석 제거 여부
         */
        public Builder stripComments(boolean strip) {
            this.stripComments = strip;
            return this;
        }

        /**
         * 태그 외부 텍스트의 HTML 엔티티 인코딩 여부
         */
        public Builder encodeEntities(boolean encode) {
            this.encodeEntities = encode;
            return this;
        }

        /**
         * HtmlSanitizer 인스턴스 생성
         */
        public HtmlSanitizer build() {
            return new HtmlSanitizer(this);
        }
    }
}
