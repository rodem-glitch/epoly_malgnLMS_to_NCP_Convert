package kr.go.growailms.security.xss;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * KISA SR1-3: 크로스 사이트 스크립트(XSS) 방지
 *
 * 출력 데이터 인코딩 유틸리티
 * - HTML Entity 인코딩
 * - JavaScript 문자열 이스케이프
 * - URL 인코딩
 * - CSS 값 이스케이프
 */
public final class OutputEncoder {

    private static final Map<Character, String> HTML_ENTITIES = Map.of(
            '&', "&amp;",
            '<', "&lt;",
            '>', "&gt;",
            '"', "&quot;",
            '\'', "&#x27;",
            '/', "&#x2F;"
    );

    private static final Pattern SCRIPT_TAG_PATTERN = Pattern.compile(
            "<script[^>]*>.*?</script>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern EVENT_HANDLER_PATTERN = Pattern.compile(
            "\\s+on\\w+\\s*=",
            Pattern.CASE_INSENSITIVE
    );

    private OutputEncoder() {
        throw new AssertionError("Utility class - instantiation not allowed");
    }

    /**
     * HTML 컨텍스트용 인코딩
     * HTML 태그 내 텍스트 콘텐츠에 사용
     */
    public static String encodeForHtml(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder encoded = new StringBuilder(input.length() * 2);
        for (char c : input.toCharArray()) {
            String entity = HTML_ENTITIES.get(c);
            if (entity != null) {
                encoded.append(entity);
            } else if (c > 127 || Character.isISOControl(c)) {
                encoded.append("&#").append((int) c).append(";");
            } else {
                encoded.append(c);
            }
        }
        return encoded.toString();
    }

    /**
     * HTML 속성값용 인코딩
     * 속성값에 삽입되는 데이터에 사용
     */
    public static String encodeForHtmlAttribute(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder encoded = new StringBuilder(input.length() * 2);
        for (char c : input.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                encoded.append(c);
            } else if (c == ' ') {
                encoded.append("&#32;");
            } else {
                encoded.append("&#").append((int) c).append(";");
            }
        }
        return encoded.toString();
    }

    /**
     * JavaScript 문자열용 이스케이프
     * JS 코드 내 문자열에 삽입되는 데이터에 사용
     */
    public static String encodeForJavaScript(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder encoded = new StringBuilder(input.length() * 2);
        for (char c : input.toCharArray()) {
            switch (c) {
                case '\'' -> encoded.append("\\'");
                case '"' -> encoded.append("\\\"");
                case '\\' -> encoded.append("\\\\");
                case '\n' -> encoded.append("\\n");
                case '\r' -> encoded.append("\\r");
                case '\t' -> encoded.append("\\t");
                case '\b' -> encoded.append("\\b");
                case '\f' -> encoded.append("\\f");
                case '<' -> encoded.append("\\u003c");
                case '>' -> encoded.append("\\u003e");
                case '&' -> encoded.append("\\u0026");
                case '/' -> encoded.append("\\/");
                default -> {
                    if (c < 32 || c > 126) {
                        encoded.append(String.format("\\u%04x", (int) c));
                    } else {
                        encoded.append(c);
                    }
                }
            }
        }
        return encoded.toString();
    }

    /**
     * JSON 값용 이스케이프
     * JSON 문자열 값에 삽입되는 데이터에 사용
     */
    public static String encodeForJson(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder encoded = new StringBuilder(input.length() * 2);
        for (char c : input.toCharArray()) {
            switch (c) {
                case '"' -> encoded.append("\\\"");
                case '\\' -> encoded.append("\\\\");
                case '\n' -> encoded.append("\\n");
                case '\r' -> encoded.append("\\r");
                case '\t' -> encoded.append("\\t");
                case '\b' -> encoded.append("\\b");
                case '\f' -> encoded.append("\\f");
                default -> {
                    if (c < 32) {
                        encoded.append(String.format("\\u%04x", (int) c));
                    } else {
                        encoded.append(c);
                    }
                }
            }
        }
        return encoded.toString();
    }

    /**
     * URL 컴포넌트용 인코딩
     * URL 경로나 쿼리 파라미터에 삽입되는 데이터에 사용
     */
    public static String encodeForUrl(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder encoded = new StringBuilder(input.length() * 3);
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            int unsignedByte = b & 0xFF;
            if (isUrlSafeChar(unsignedByte)) {
                encoded.append((char) unsignedByte);
            } else {
                encoded.append('%');
                encoded.append(Character.toUpperCase(Character.forDigit((unsignedByte >> 4) & 0xF, 16)));
                encoded.append(Character.toUpperCase(Character.forDigit(unsignedByte & 0xF, 16)));
            }
        }
        return encoded.toString();
    }

    /**
     * CSS 값용 이스케이프
     * CSS 속성값에 삽입되는 데이터에 사용
     */
    public static String encodeForCss(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder encoded = new StringBuilder(input.length() * 2);
        for (char c : input.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                encoded.append(c);
            } else {
                encoded.append('\\').append(String.format("%06x", (int) c));
            }
        }
        return encoded.toString();
    }

    /**
     * LDAP DN용 이스케이프
     */
    public static String encodeForLdapDn(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder encoded = new StringBuilder(input.length() * 2);
        for (char c : input.toCharArray()) {
            switch (c) {
                case '\\', ',', '+', '"', '<', '>', ';', '#', '=' -> {
                    encoded.append('\\');
                    encoded.append(c);
                }
                default -> {
                    if (c < 32 || c > 126) {
                        encoded.append(String.format("\\%02x", (int) c));
                    } else {
                        encoded.append(c);
                    }
                }
            }
        }
        return encoded.toString();
    }

    /**
     * XML 콘텐츠용 인코딩
     */
    public static String encodeForXml(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder encoded = new StringBuilder(input.length() * 2);
        for (char c : input.toCharArray()) {
            switch (c) {
                case '&' -> encoded.append("&amp;");
                case '<' -> encoded.append("&lt;");
                case '>' -> encoded.append("&gt;");
                case '"' -> encoded.append("&quot;");
                case '\'' -> encoded.append("&apos;");
                default -> {
                    if (c < 32 && c != '\t' && c != '\n' && c != '\r') {
                        // XML 1.0에서 허용되지 않는 제어 문자 제거
                        encoded.append(' ');
                    } else {
                        encoded.append(c);
                    }
                }
            }
        }
        return encoded.toString();
    }

    /**
     * 스크립트 태그 제거
     */
    public static String removeScriptTags(String input) {
        if (input == null) {
            return null;
        }
        return SCRIPT_TAG_PATTERN.matcher(input).replaceAll("");
    }

    /**
     * 이벤트 핸들러 속성 제거
     */
    public static String removeEventHandlers(String input) {
        if (input == null) {
            return null;
        }
        return EVENT_HANDLER_PATTERN.matcher(input).replaceAll(" ");
    }

    /**
     * 기본 XSS 정제 (스크립트 태그 + 이벤트 핸들러 제거)
     */
    public static String sanitizeXss(String input) {
        if (input == null) {
            return null;
        }
        String result = removeScriptTags(input);
        result = removeEventHandlers(result);
        return result;
    }

    private static boolean isUrlSafeChar(int c) {
        return (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') ||
                c == '-' || c == '_' || c == '.' || c == '~';
    }
}
