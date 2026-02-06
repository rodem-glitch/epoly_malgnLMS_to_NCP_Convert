package kr.go.growailms.security.logging;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KISA SR4-2: 보안 로깅
 *
 * 로그 데이터 정제 유틸리티
 * - 민감 정보 마스킹
 * - 로그 인젝션 방지
 * - PII 데이터 보호
 */
public final class LogSanitizer {

    private static final String MASK = "********";
    private static final String PARTIAL_MASK = "***";

    // 민감한 필드명 패턴
    private static final Set<String> SENSITIVE_FIELD_NAMES = Set.of(
            "password", "passwd", "pwd", "secret", "token", "apikey", "api_key",
            "accesstoken", "access_token", "refreshtoken", "refresh_token",
            "credential", "credentials", "auth", "authorization",
            "ssn", "social_security", "credit_card", "creditcard", "card_number",
            "cvv", "cvc", "pin", "private_key", "privatekey"
    );

    // 이메일 패턴
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    );

    // 전화번호 패턴 (한국 형식)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(01[016789])[-.\\s]?(\\d{3,4})[-.\\s]?(\\d{4})"
    );

    // 주민등록번호 패턴
    private static final Pattern SSN_PATTERN = Pattern.compile(
            "(\\d{6})[-.\\s]?(\\d{7})"
    );

    // 카드번호 패턴
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "(\\d{4})[-.\\s]?(\\d{4})[-.\\s]?(\\d{4})[-.\\s]?(\\d{4})"
    );

    // IP 주소 패턴
    private static final Pattern IP_PATTERN = Pattern.compile(
            "(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})"
    );

    private LogSanitizer() {
        throw new AssertionError("Utility class - instantiation not allowed");
    }

    /**
     * 문자열 내 민감 정보 마스킹
     */
    public static String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String result = input;
        result = maskEmails(result);
        result = maskPhoneNumbers(result);
        result = maskSocialSecurityNumbers(result);
        result = maskCreditCards(result);
        result = removeControlCharacters(result);

        return result;
    }

    /**
     * 전체 마스킹 (민감 필드용)
     */
    public static String maskFully(String input) {
        if (input == null) {
            return null;
        }
        return MASK;
    }

    /**
     * 부분 마스킹 (앞뒤 일부만 표시)
     */
    public static String maskPartially(String input, int visiblePrefix, int visibleSuffix) {
        if (input == null) {
            return null;
        }
        int length = input.length();
        if (length <= visiblePrefix + visibleSuffix) {
            return PARTIAL_MASK;
        }
        return input.substring(0, visiblePrefix) + PARTIAL_MASK + input.substring(length - visibleSuffix);
    }

    /**
     * 이메일 마스킹
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return PARTIAL_MASK;
        }
        int atIndex = email.indexOf('@');
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        if (local.length() <= 2) {
            return PARTIAL_MASK + domain;
        }
        return local.charAt(0) + PARTIAL_MASK + local.charAt(local.length() - 1) + domain;
    }

    /**
     * 전화번호 마스킹
     */
    public static String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 10) {
            return PARTIAL_MASK;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 10) {
            return PARTIAL_MASK;
        }
        return digits.substring(0, 3) + "-" + PARTIAL_MASK + "-" + digits.substring(digits.length() - 4);
    }

    /**
     * 주민등록번호 마스킹
     */
    public static String maskSsn(String ssn) {
        if (ssn == null) {
            return PARTIAL_MASK;
        }
        String digits = ssn.replaceAll("[^0-9]", "");
        if (digits.length() != 13) {
            return PARTIAL_MASK;
        }
        return digits.substring(0, 6) + "-" + PARTIAL_MASK;
    }

    /**
     * 신용카드 번호 마스킹
     */
    public static String maskCreditCard(String cardNumber) {
        if (cardNumber == null) {
            return PARTIAL_MASK;
        }
        String digits = cardNumber.replaceAll("[^0-9]", "");
        if (digits.length() < 13 || digits.length() > 19) {
            return PARTIAL_MASK;
        }
        return digits.substring(0, 4) + "-" + PARTIAL_MASK + "-" + PARTIAL_MASK + "-" + digits.substring(digits.length() - 4);
    }

    /**
     * IP 주소 마스킹 (마지막 옥텟)
     */
    public static String maskIpAddress(String ip) {
        if (ip == null) {
            return PARTIAL_MASK;
        }
        Matcher matcher = IP_PATTERN.matcher(ip);
        if (matcher.matches()) {
            return matcher.group(1) + "." + matcher.group(2) + "." + matcher.group(3) + ".***";
        }
        return PARTIAL_MASK;
    }

    /**
     * Map에서 민감 필드 마스킹
     */
    public static void maskSensitiveFields(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        for (String key : data.keySet()) {
            if (isSensitiveField(key)) {
                data.put(key, MASK);
            } else if (data.get(key) instanceof String stringValue) {
                data.put(key, sanitize(stringValue));
            } else if (data.get(key) instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) data.get(key);
                maskSensitiveFields(nestedMap);
            }
        }
    }

    /**
     * 민감 필드명 여부 확인
     */
    public static boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String normalized = fieldName.toLowerCase().replaceAll("[_-]", "");
        return SENSITIVE_FIELD_NAMES.stream()
                .anyMatch(sensitive -> normalized.contains(sensitive.replaceAll("[_-]", "")));
    }

    /**
     * 제어 문자 제거 (로그 인젝션 방지)
     */
    public static String removeControlCharacters(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("[\\x00-\\x1F\\x7F]", " ");
    }

    /**
     * 줄바꿈 이스케이프 (로그 인젝션 방지)
     */
    public static String escapeNewlines(String input) {
        if (input == null) {
            return null;
        }
        return input
                .replace("\r\n", "\\r\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /**
     * 길이 제한
     */
    public static String truncate(String input, int maxLength) {
        if (input == null || input.length() <= maxLength) {
            return input;
        }
        return input.substring(0, maxLength) + "...[truncated]";
    }

    private static String maskEmails(String input) {
        Matcher matcher = EMAIL_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String email = matcher.group();
            matcher.appendReplacement(result, Matcher.quoteReplacement(maskEmail(email)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String maskPhoneNumbers(String input) {
        Matcher matcher = PHONE_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String replacement = matcher.group(1) + "-" + PARTIAL_MASK + "-" + matcher.group(3);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String maskSocialSecurityNumbers(String input) {
        Matcher matcher = SSN_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, matcher.group(1) + "-*******");
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String maskCreditCards(String input) {
        Matcher matcher = CREDIT_CARD_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String replacement = matcher.group(1) + "-****-****-" + matcher.group(4);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
