package kr.go.growailms.security.validation;

import java.util.Set;

/**
 * KISA SR1-1: 입력 데이터 검증 및 표현
 *
 * 비즈니스 규칙에 따른 검증 규칙 정의
 * - 파라미터별 허용 값 정의
 * - 범위 및 형식 규칙 중앙 관리
 */
public final class ValidationRules {

    private ValidationRules() {
        throw new AssertionError("Utility class - instantiation not allowed");
    }

    // 페이징 관련 규칙
    public static final int MIN_PAGE_NUMBER = 1;
    public static final int MAX_PAGE_NUMBER = 10000;
    public static final int DEFAULT_PAGE_NUMBER = 1;
    public static final int MIN_PAGE_SIZE = 1;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 10;

    // 검색어 관련 규칙
    public static final int MIN_KEYWORD_LENGTH = 1;
    public static final int MAX_KEYWORD_LENGTH = 200;

    // 토큰 관련 규칙
    public static final int MIN_TOKEN_LENGTH = 16;
    public static final int MAX_TOKEN_LENGTH = 512;

    // 코드 관련 규칙
    public static final int MIN_CODE_LENGTH = 1;
    public static final int MAX_CODE_LENGTH = 50;

    // depthType 허용 값
    public static final Set<String> ALLOWED_DEPTH_TYPES = Set.of("1", "2", "3");

    // provider 허용 값
    public static final Set<String> ALLOWED_PROVIDERS = Set.of("WORK24", "JOBKOREA", "ALL");

    // cachePolicy 허용 값
    public static final Set<String> ALLOWED_CACHE_POLICIES = Set.of("PREFER_CACHE", "FORCE_LIVE", "CACHE_ONLY");

    // salTp (급여 유형) 허용 값
    public static final Set<String> ALLOWED_SALARY_TYPES = Set.of("H", "D", "M", "Y");

    // education (학력) 허용 값
    public static final Set<String> ALLOWED_EDUCATION_CODES = Set.of("00", "01", "02", "03", "04", "05", "06", "07");

    /**
     * 페이지 번호 정규화
     */
    public static int normalizePageNumber(Integer value) {
        if (value == null || value < MIN_PAGE_NUMBER) {
            return DEFAULT_PAGE_NUMBER;
        }
        return Math.min(value, MAX_PAGE_NUMBER);
    }

    /**
     * 페이지 크기 정규화
     */
    public static int normalizePageSize(Integer value) {
        if (value == null || value < MIN_PAGE_SIZE) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(value, MAX_PAGE_SIZE);
    }

    /**
     * depthType 검증
     */
    public static boolean isValidDepthType(String value) {
        return value != null && ALLOWED_DEPTH_TYPES.contains(value.trim());
    }

    /**
     * depthType 정규화 (기본값 반환)
     */
    public static String normalizeDepthType(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String trimmed = value.trim();
        return ALLOWED_DEPTH_TYPES.contains(trimmed) ? trimmed : defaultValue;
    }

    /**
     * provider 검증
     */
    public static boolean isValidProvider(String value) {
        if (value == null || value.isBlank()) {
            return true; // null은 기본값 사용
        }
        return ALLOWED_PROVIDERS.contains(value.trim().toUpperCase());
    }

    /**
     * provider 정규화
     */
    public static String normalizeProvider(String value) {
        if (value == null || value.isBlank()) {
            return "WORK24";
        }
        String upper = value.trim().toUpperCase();
        return ALLOWED_PROVIDERS.contains(upper) ? upper : "WORK24";
    }

    /**
     * cachePolicy 검증
     */
    public static boolean isValidCachePolicy(String value) {
        if (value == null || value.isBlank()) {
            return true; // null은 기본값 사용
        }
        return ALLOWED_CACHE_POLICIES.contains(value.trim().toUpperCase());
    }

    /**
     * cachePolicy 정규화
     */
    public static String normalizeCachePolicy(String value) {
        if (value == null || value.isBlank()) {
            return "PREFER_CACHE";
        }
        String upper = value.trim().toUpperCase();
        return ALLOWED_CACHE_POLICIES.contains(upper) ? upper : "PREFER_CACHE";
    }

    /**
     * 급여 유형 검증
     */
    public static boolean isValidSalaryType(String value) {
        if (value == null || value.isBlank()) {
            return true; // null은 허용
        }
        return ALLOWED_SALARY_TYPES.contains(value.trim().toUpperCase());
    }

    /**
     * 급여 유형 정규화
     */
    public static String normalizeSalaryType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String upper = value.trim().toUpperCase();
        return ALLOWED_SALARY_TYPES.contains(upper) ? upper : null;
    }

    /**
     * 학력 코드 검증
     */
    public static boolean isValidEducationCode(String value) {
        if (value == null || value.isBlank()) {
            return true; // null은 허용
        }
        return ALLOWED_EDUCATION_CODES.contains(value.trim());
    }

    /**
     * 학력 코드 정규화
     */
    public static String normalizeEducationCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return ALLOWED_EDUCATION_CODES.contains(trimmed) ? trimmed : null;
    }

    /**
     * 급여 금액 검증
     */
    public static boolean isValidPayAmount(Integer value) {
        return value == null || value >= 0;
    }

    /**
     * 급여 금액 정규화
     */
    public static Integer normalizePayAmount(Integer value) {
        if (value == null) {
            return null;
        }
        return value < 0 ? null : value;
    }

    /**
     * 검색어 정규화
     */
    public static String normalizeKeyword(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            return trimmed.substring(0, MAX_KEYWORD_LENGTH);
        }
        return trimmed;
    }
}
