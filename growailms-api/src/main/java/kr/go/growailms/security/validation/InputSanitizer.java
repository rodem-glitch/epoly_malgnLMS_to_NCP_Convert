package kr.go.growailms.security.validation;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * KISA SR1-1: 입력 데이터 검증 및 표현
 *
 * 모든 외부 입력값에 대한 정제(Sanitization) 기능 제공
 * - 특수문자 필터링
 * - 길이 제한
 * - 인코딩 정규화
 */
public final class InputSanitizer {

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x1F\\x7F]");
    private static final Pattern SQL_INJECTION_CHARS = Pattern.compile("['\";\\\\]");
    private static final Pattern PATH_TRAVERSAL = Pattern.compile("\\.\\.|/|\\\\");
    private static final int DEFAULT_MAX_LENGTH = 1000;
    private static final int SHORT_TEXT_MAX = 100;
    private static final int MEDIUM_TEXT_MAX = 500;

    private InputSanitizer() {
        throw new AssertionError("Utility class - instantiation not allowed");
    }

    /**
     * 일반 텍스트 정제 (제어문자 제거, 길이 제한)
     */
    public static String sanitizeText(String input) {
        return sanitizeText(input, DEFAULT_MAX_LENGTH);
    }

    /**
     * 지정된 최대 길이로 텍스트 정제
     */
    public static String sanitizeText(String input, int maxLength) {
        if (input == null) {
            return null;
        }

        String normalized = normalizeEncoding(input);
        String cleaned = removeControlCharacters(normalized);
        return truncate(cleaned, maxLength);
    }

    /**
     * 짧은 텍스트용 정제 (100자 제한)
     */
    public static String sanitizeShortText(String input) {
        return sanitizeText(input, SHORT_TEXT_MAX);
    }

    /**
     * 중간 길이 텍스트용 정제 (500자 제한)
     */
    public static String sanitizeMediumText(String input) {
        return sanitizeText(input, MEDIUM_TEXT_MAX);
    }

    /**
     * 숫자 문자열 정제 (숫자만 허용)
     */
    public static String sanitizeNumeric(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("[^0-9]", "");
    }

    /**
     * 영숫자 문자열 정제 (영문, 숫자만 허용)
     */
    public static String sanitizeAlphanumeric(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("[^a-zA-Z0-9]", "");
    }

    /**
     * 식별자용 정제 (영문, 숫자, 언더스코어, 하이픈만 허용)
     */
    public static String sanitizeIdentifier(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("[^a-zA-Z0-9_\\-]", "");
    }

    /**
     * SQL 쿼리 파라미터용 정제 (위험 문자 이스케이프)
     * 주의: Prepared Statement 사용이 기본이며, 이 메서드는 추가 방어용
     */
    public static String sanitizeForSql(String input) {
        if (input == null) {
            return null;
        }
        return SQL_INJECTION_CHARS.matcher(input).replaceAll("");
    }

    /**
     * 파일 경로용 정제 (경로 순회 공격 방지)
     */
    public static String sanitizeFilePath(String input) {
        if (input == null) {
            return null;
        }
        return PATH_TRAVERSAL.matcher(input).replaceAll("");
    }

    /**
     * 제어 문자 제거
     */
    public static String removeControlCharacters(String input) {
        if (input == null) {
            return null;
        }
        return CONTROL_CHARS.matcher(input).replaceAll("");
    }

    /**
     * 인코딩 정규화 (UTF-8)
     */
    public static String normalizeEncoding(String input) {
        if (input == null) {
            return null;
        }
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 문자열 길이 제한
     */
    public static String truncate(String input, int maxLength) {
        Objects.requireNonNull(input, "Input cannot be null for truncation");
        if (maxLength <= 0) {
            throw new IllegalArgumentException("Max length must be positive");
        }
        if (input.length() <= maxLength) {
            return input;
        }
        return input.substring(0, maxLength);
    }

    /**
     * 공백 정규화 (연속 공백을 단일 공백으로)
     */
    public static String normalizeWhitespace(String input) {
        if (input == null) {
            return null;
        }
        return input.trim().replaceAll("\\s+", " ");
    }

    /**
     * 빈 문자열을 null로 변환
     */
    public static String emptyToNull(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * null을 빈 문자열로 변환
     */
    public static String nullToEmpty(String input) {
        return input == null ? "" : input;
    }
}
