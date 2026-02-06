package kr.go.growailms.security.authentication;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * KISA SR2-1: 보안 기능 - 적절한 인증 기능
 *
 * 관리자 인증 기능 제공
 * - 토큰 기반 인증
 * - 로컬 접근 허용
 * - 인증 실패 횟수 제한 (Brute Force 방지)
 */
@Component
public class AdminAuthenticator {

    private static final String LOCALHOST_IPV4 = "127.0.0.1";
    private static final String LOCALHOST_IPV6 = "::1";
    private static final String LOCALHOST_IPV6_MAPPED = "0:0:0:0:0:0:0:1";

    // 인증 실패 제한 설정
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 300_000; // 5분

    // IP별 인증 실패 추적
    private final ConcurrentHashMap<String, FailedAttemptInfo> failedAttempts = new ConcurrentHashMap<>();

    /**
     * 관리자 토큰 인증 수행
     *
     * @param providedToken 요청에서 제공된 토큰
     * @param envTokenKey 환경변수에서 기대 토큰을 가져올 키
     * @param request HTTP 요청 객체
     * @throws ResponseStatusException 인증 실패 시
     */
    public void authenticate(String providedToken, String envTokenKey, HttpServletRequest request) {
        String clientIp = extractClientIp(request);

        // 잠금 상태 확인
        if (isLockedOut(clientIp)) {
            throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many failed attempts. Please try again later."
            );
        }

        String expectedToken = System.getenv(envTokenKey);

        // 환경변수에 토큰이 설정되어 있는 경우
        if (expectedToken != null && !expectedToken.isBlank()) {
            if (providedToken == null || providedToken.isBlank()) {
                recordFailedAttempt(clientIp);
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Authentication token is required."
                );
            }

            // 상수 시간 비교로 Timing Attack 방지
            if (!SecureTokenComparator.secureEquals(providedToken, expectedToken)) {
                recordFailedAttempt(clientIp);
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Invalid authentication token."
                );
            }

            // 인증 성공 시 실패 카운트 초기화
            clearFailedAttempts(clientIp);
            return;
        }

        // 환경변수에 토큰이 없는 경우: 로컬 접근만 허용
        if (!isLocalRequest(clientIp)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Local access only. Configure " + envTokenKey + " for remote access."
            );
        }

        clearFailedAttempts(clientIp);
    }

    /**
     * 웹훅 토큰 인증 수행
     *
     * @param providedToken 요청에서 제공된 토큰
     * @param envTokenKey 환경변수에서 기대 토큰을 가져올 키
     * @param request HTTP 요청 객체
     * @throws ResponseStatusException 인증 실패 시
     */
    public void authenticateWebhook(String providedToken, String envTokenKey, HttpServletRequest request) {
        String clientIp = extractClientIp(request);

        if (isLockedOut(clientIp)) {
            throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many failed attempts. Please try again later."
            );
        }

        String expectedToken = System.getenv(envTokenKey);

        if (expectedToken != null && !expectedToken.isBlank()) {
            if (!SecureTokenComparator.secureEquals(providedToken, expectedToken)) {
                recordFailedAttempt(clientIp);
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Invalid webhook token."
                );
            }
            clearFailedAttempts(clientIp);
            return;
        }

        // 토큰 미설정 시 로컬만 허용
        if (!isLocalRequest(clientIp)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Local access only. Configure " + envTokenKey + " for external webhooks."
            );
        }

        clearFailedAttempts(clientIp);
    }

    /**
     * 로컬 요청 여부 확인
     */
    public boolean isLocalRequest(String clientIp) {
        if (clientIp == null) {
            return false;
        }
        return LOCALHOST_IPV4.equals(clientIp)
            || LOCALHOST_IPV6.equals(clientIp)
            || LOCALHOST_IPV6_MAPPED.equals(clientIp);
    }

    /**
     * 클라이언트 IP 추출 (프록시 고려)
     */
    public String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        // X-Forwarded-For 헤더 확인 (프록시/로드밸런서 뒤에 있는 경우)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // 첫 번째 IP가 실제 클라이언트 IP
            String[] ips = xForwardedFor.split(",");
            if (ips.length > 0) {
                return ips[0].trim();
            }
        }

        // X-Real-IP 헤더 확인 (Nginx 등)
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }

        return request.getRemoteAddr();
    }

    /**
     * 잠금 상태 확인
     */
    private boolean isLockedOut(String clientIp) {
        if (clientIp == null) {
            return false;
        }

        FailedAttemptInfo info = failedAttempts.get(clientIp);
        if (info == null) {
            return false;
        }

        // 잠금 기간이 지났으면 잠금 해제
        if (System.currentTimeMillis() - info.lastFailedTime > LOCKOUT_DURATION_MS) {
            failedAttempts.remove(clientIp);
            return false;
        }

        return info.failedCount.get() >= MAX_FAILED_ATTEMPTS;
    }

    /**
     * 인증 실패 기록
     */
    private void recordFailedAttempt(String clientIp) {
        if (clientIp == null) {
            return;
        }

        failedAttempts.compute(clientIp, (ip, existing) -> {
            if (existing == null) {
                return new FailedAttemptInfo();
            }

            // 잠금 기간이 지났으면 초기화
            if (System.currentTimeMillis() - existing.lastFailedTime > LOCKOUT_DURATION_MS) {
                return new FailedAttemptInfo();
            }

            existing.failedCount.incrementAndGet();
            existing.lastFailedTime = System.currentTimeMillis();
            return existing;
        });
    }

    /**
     * 인증 성공 시 실패 기록 초기화
     */
    private void clearFailedAttempts(String clientIp) {
        if (clientIp != null) {
            failedAttempts.remove(clientIp);
        }
    }

    /**
     * 인증 실패 정보
     */
    private static class FailedAttemptInfo {
        final AtomicInteger failedCount = new AtomicInteger(1);
        volatile long lastFailedTime = System.currentTimeMillis();
    }
}
