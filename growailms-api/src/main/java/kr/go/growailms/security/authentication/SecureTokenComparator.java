package kr.go.growailms.security.authentication;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * KISA SR2-1: 보안 기능 - 적절한 인증 기능
 *
 * Timing Attack 방지를 위한 상수 시간 토큰 비교 기능 제공
 * - MessageDigest.isEqual() 사용
 * - 문자열 길이와 무관하게 일정 시간 소요
 */
public final class SecureTokenComparator {

    private static final String HASH_ALGORITHM = "SHA-256";

    private SecureTokenComparator() {
        throw new AssertionError("Utility class - instantiation not allowed");
    }

    /**
     * 두 토큰을 상수 시간에 비교 (Timing Attack 방지)
     *
     * @param providedToken 사용자가 제공한 토큰
     * @param expectedToken 시스템에 저장된 기대 토큰
     * @return 토큰이 일치하면 true, 아니면 false
     */
    public static boolean secureEquals(String providedToken, String expectedToken) {
        if (providedToken == null || expectedToken == null) {
            // null 처리도 상수 시간에 수행
            return secureEqualsNull(providedToken, expectedToken);
        }

        byte[] providedBytes = providedToken.getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = expectedToken.getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(providedBytes, expectedBytes);
    }

    /**
     * 두 토큰의 해시값을 비교 (추가 보안 레이어)
     *
     * @param providedToken 사용자가 제공한 토큰
     * @param expectedToken 시스템에 저장된 기대 토큰
     * @return 해시값이 일치하면 true, 아니면 false
     */
    public static boolean secureHashEquals(String providedToken, String expectedToken) {
        if (providedToken == null || expectedToken == null) {
            return secureEqualsNull(providedToken, expectedToken);
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);

            byte[] providedHash = digest.digest(
                providedToken.getBytes(StandardCharsets.UTF_8)
            );

            digest.reset();

            byte[] expectedHash = digest.digest(
                expectedToken.getBytes(StandardCharsets.UTF_8)
            );

            return MessageDigest.isEqual(providedHash, expectedHash);

        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 JVM에서 지원하므로 발생하지 않음
            // 발생 시 false 반환 (안전한 실패)
            return false;
        }
    }

    /**
     * 바이트 배열을 상수 시간에 비교
     *
     * @param provided 제공된 바이트 배열
     * @param expected 기대 바이트 배열
     * @return 배열이 일치하면 true, 아니면 false
     */
    public static boolean secureEquals(byte[] provided, byte[] expected) {
        if (provided == null || expected == null) {
            return provided == expected;
        }
        return MessageDigest.isEqual(provided, expected);
    }

    /**
     * null 값 상수 시간 비교
     */
    private static boolean secureEqualsNull(String a, String b) {
        // XOR 연산으로 상수 시간 비교
        int result = (a == null ? 1 : 0) ^ (b == null ? 1 : 0);

        // 추가 더미 연산으로 시간 일정하게 유지
        @SuppressWarnings("unused")
        int dummy = 0;
        for (int i = 0; i < 100; i++) {
            dummy ^= i;
        }

        return result == 0 && a == null && b == null;
    }

    /**
     * 토큰 유효성 기본 검사
     *
     * @param token 검사할 토큰
     * @param minLength 최소 길이
     * @param maxLength 최대 길이
     * @return 유효하면 true, 아니면 false
     */
    public static boolean isValidTokenFormat(String token, int minLength, int maxLength) {
        if (token == null) {
            return false;
        }

        int length = token.length();
        if (length < minLength || length > maxLength) {
            return false;
        }

        // 토큰에 허용되지 않는 문자 검사
        for (char c : token.toCharArray()) {
            if (!isAllowedTokenCharacter(c)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 토큰에 허용되는 문자 검사
     */
    private static boolean isAllowedTokenCharacter(char c) {
        return (c >= 'a' && c <= 'z')
            || (c >= 'A' && c <= 'Z')
            || (c >= '0' && c <= '9')
            || c == '-'
            || c == '_'
            || c == '.';
    }

    /**
     * 토큰 마스킹 (로깅용)
     * 토큰의 처음과 끝 일부만 표시하고 나머지는 마스킹
     *
     * @param token 마스킹할 토큰
     * @return 마스킹된 토큰 문자열
     */
    public static String maskToken(String token) {
        if (token == null) {
            return "[null]";
        }

        int length = token.length();
        if (length <= 8) {
            return "****";
        }

        int visibleChars = Math.min(4, length / 4);
        String prefix = token.substring(0, visibleChars);
        String suffix = token.substring(length - visibleChars);
        int maskedLength = length - (visibleChars * 2);

        return prefix + "*".repeat(Math.min(maskedLength, 8)) + suffix;
    }
}
