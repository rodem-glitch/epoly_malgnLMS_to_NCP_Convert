package kr.go.growailms.security.validation;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * KISA SR1-1: 입력 데이터 검증 및 표현
 *
 * 입력 파라미터에 대한 유효성 검증 기능 제공
 * - 범위 검사
 * - 형식 검사
 * - 화이트리스트 검사
 */
public final class ParameterValidator {

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern PHONE_PATTERN =
        Pattern.compile("^\\d{2,4}-?\\d{3,4}-?\\d{4}$");
    private static final Pattern UUID_PATTERN =
        Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern SAFE_STRING_PATTERN =
        Pattern.compile("^[a-zA-Z0-9가-힣\\s\\-_.,!?()]+$");

    private ParameterValidator() {
        throw new AssertionError("Utility class - instantiation not allowed");
    }

    /**
     * null 또는 공백 문자열 검사
     */
    public static boolean isNullOrBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 문자열이 비어있지 않은지 검사
     */
    public static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 문자열 길이 범위 검사
     */
    public static boolean isLengthInRange(String value, int minLength, int maxLength) {
        if (value == null) {
            return minLength == 0;
        }
        int length = value.length();
        return length >= minLength && length <= maxLength;
    }

    /**
     * 숫자 범위 검사
     */
    public static boolean isInRange(int value, int min, int max) {
        return value >= min && value <= max;
    }

    /**
     * Long 타입 숫자 범위 검사
     */
    public static boolean isInRange(long value, long min, long max) {
        return value >= min && value <= max;
    }

    /**
     * 양수 검사
     */
    public static boolean isPositive(Integer value) {
        return value != null && value > 0;
    }

    /**
     * 양수 또는 0 검사
     */
    public static boolean isNonNegative(Integer value) {
        return value != null && value >= 0;
    }

    /**
     * 화이트리스트 검사
     */
    public static <T> boolean isInWhitelist(T value, Set<T> whitelist) {
        Objects.requireNonNull(whitelist, "Whitelist cannot be null");
        return value != null && whitelist.contains(value);
    }

    /**
     * 화이트리스트 검사 (가변 인자)
     */
    @SafeVarargs
    public static <T> boolean isOneOf(T value, T... allowedValues) {
        if (value == null || allowedValues == null) {
            return false;
        }
        for (T allowed : allowedValues) {
            if (value.equals(allowed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 이메일 형식 검사
     */
    public static boolean isValidEmail(String value) {
        return isNotBlank(value) && EMAIL_PATTERN.matcher(value).matches();
    }

    /**
     * 전화번호 형식 검사
     */
    public static boolean isValidPhone(String value) {
        return isNotBlank(value) && PHONE_PATTERN.matcher(value).matches();
    }

    /**
     * UUID 형식 검사
     */
    public static boolean isValidUuid(String value) {
        return isNotBlank(value) && UUID_PATTERN.matcher(value).matches();
    }

    /**
     * 안전한 문자열 검사 (한글, 영문, 숫자, 기본 특수문자만 허용)
     */
    public static boolean isSafeString(String value) {
        return isNotBlank(value) && SAFE_STRING_PATTERN.matcher(value).matches();
    }

    /**
     * 숫자 문자열 검사
     */
    public static boolean isNumericString(String value) {
        if (isNullOrBlank(value)) {
            return false;
        }
        for (char c : value.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 영숫자 문자열 검사
     */
    public static boolean isAlphanumeric(String value) {
        if (isNullOrBlank(value)) {
            return false;
        }
        for (char c : value.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 컬렉션이 비어있지 않은지 검사
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return collection != null && !collection.isEmpty();
    }

    /**
     * 정규식 패턴 매칭 검사
     */
    public static boolean matchesPattern(String value, Pattern pattern) {
        Objects.requireNonNull(pattern, "Pattern cannot be null");
        return isNotBlank(value) && pattern.matcher(value).matches();
    }

    /**
     * 정규식 패턴 매칭 검사 (문자열 패턴)
     */
    public static boolean matchesPattern(String value, String patternString) {
        Objects.requireNonNull(patternString, "Pattern string cannot be null");
        return matchesPattern(value, Pattern.compile(patternString));
    }

    /**
     * 검증 실패 시 예외 발생
     */
    public static void requireNotBlank(String value, String parameterName) {
        if (isNullOrBlank(value)) {
            throw new IllegalArgumentException(
                String.format("Parameter '%s' must not be null or blank", parameterName)
            );
        }
    }

    /**
     * 범위 검증 실패 시 예외 발생
     */
    public static void requireInRange(int value, int min, int max, String parameterName) {
        if (!isInRange(value, min, max)) {
            throw new IllegalArgumentException(
                String.format("Parameter '%s' must be between %d and %d", parameterName, min, max)
            );
        }
    }

    /**
     * 양수 검증 실패 시 예외 발생
     */
    public static void requirePositive(Integer value, String parameterName) {
        if (!isPositive(value)) {
            throw new IllegalArgumentException(
                String.format("Parameter '%s' must be a positive integer", parameterName)
            );
        }
    }
}
