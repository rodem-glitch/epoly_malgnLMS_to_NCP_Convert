package kr.go.growailms.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * KISA 시큐어코딩 보안 설정
 *
 * 보안 관련 전역 설정 관리
 * - 입력값 검증 설정
 * - 인증/인가 설정
 * - 로깅 설정
 */
@Configuration
public class SecurityConfiguration {

    // 입력값 검증 설정
    @Value("${security.input.max-length:10000}")
    private int inputMaxLength;

    @Value("${security.input.max-file-size:10485760}")
    private long maxFileSize;

    @Value("${security.input.allowed-content-types:application/json,application/xml,text/plain}")
    private List<String> allowedContentTypes;

    // 인증 설정
    @Value("${security.auth.token-expiry-minutes:30}")
    private int tokenExpiryMinutes;

    @Value("${security.auth.max-login-attempts:5}")
    private int maxLoginAttempts;

    @Value("${security.auth.lockout-duration-minutes:30}")
    private int lockoutDurationMinutes;

    // 로깅 설정
    @Value("${security.logging.mask-sensitive:true}")
    private boolean maskSensitiveData;

    @Value("${security.logging.max-log-length:1000}")
    private int maxLogLength;

    /**
     * 입력 검증 설정 빈
     */
    @Bean
    public InputValidationConfig inputValidationConfig() {
        return new InputValidationConfig(
                inputMaxLength,
                maxFileSize,
                Set.copyOf(allowedContentTypes),
                StandardCharsets.UTF_8
        );
    }

    /**
     * 인증 설정 빈
     */
    @Bean
    public AuthenticationConfig authenticationConfig() {
        return new AuthenticationConfig(
                Duration.ofMinutes(tokenExpiryMinutes),
                maxLoginAttempts,
                Duration.ofMinutes(lockoutDurationMinutes)
        );
    }

    /**
     * 로깅 설정 빈
     */
    @Bean
    public LoggingConfig loggingConfig() {
        return new LoggingConfig(
                maskSensitiveData,
                maxLogLength
        );
    }

    /**
     * 입력 검증 설정 레코드
     */
    public record InputValidationConfig(
            int maxLength,
            long maxFileSize,
            Set<String> allowedContentTypes,
            Charset defaultCharset
    ) {
        public boolean isContentTypeAllowed(String contentType) {
            if (contentType == null) {
                return false;
            }
            String normalized = contentType.split(";")[0].trim().toLowerCase();
            return allowedContentTypes.contains(normalized);
        }

        public boolean isFileSizeValid(long size) {
            return size > 0 && size <= maxFileSize;
        }
    }

    /**
     * 인증 설정 레코드
     */
    public record AuthenticationConfig(
            Duration tokenExpiry,
            int maxLoginAttempts,
            Duration lockoutDuration
    ) {
        public boolean isAccountLocked(int failedAttempts) {
            return failedAttempts >= maxLoginAttempts;
        }

        public long getTokenExpiryMillis() {
            return tokenExpiry.toMillis();
        }

        public long getLockoutDurationMillis() {
            return lockoutDuration.toMillis();
        }
    }

    /**
     * 로깅 설정 레코드
     */
    public record LoggingConfig(
            boolean maskSensitiveData,
            int maxLogLength
    ) {
        public String truncateIfNeeded(String message) {
            if (message == null || message.length() <= maxLogLength) {
                return message;
            }
            return message.substring(0, maxLogLength) + "...[truncated]";
        }
    }

    /**
     * CORS 허용 출처 설정
     */
    @Bean
    public CorsConfig corsConfig(
            @Value("${security.cors.allowed-origins:}") List<String> allowedOrigins,
            @Value("${security.cors.allowed-methods:GET,POST,PUT,DELETE}") List<String> allowedMethods,
            @Value("${security.cors.max-age:3600}") long maxAge
    ) {
        return new CorsConfig(
                Set.copyOf(allowedOrigins),
                Set.copyOf(allowedMethods),
                maxAge
        );
    }

    /**
     * CORS 설정 레코드
     */
    public record CorsConfig(
            Set<String> allowedOrigins,
            Set<String> allowedMethods,
            long maxAge
    ) {
        public boolean isOriginAllowed(String origin) {
            if (allowedOrigins.isEmpty()) {
                return true; // 설정 없으면 모두 허용 (개발 환경용)
            }
            return allowedOrigins.contains(origin);
        }

        public boolean isMethodAllowed(String method) {
            return allowedMethods.contains(method.toUpperCase());
        }
    }

    /**
     * Rate Limiting 설정
     */
    @Bean
    public RateLimitConfig rateLimitConfig(
            @Value("${security.rate-limit.requests-per-minute:60}") int requestsPerMinute,
            @Value("${security.rate-limit.burst-capacity:10}") int burstCapacity
    ) {
        return new RateLimitConfig(requestsPerMinute, burstCapacity);
    }

    /**
     * Rate Limit 설정 레코드
     */
    public record RateLimitConfig(
            int requestsPerMinute,
            int burstCapacity
    ) {
        public double getRefillRate() {
            return requestsPerMinute / 60.0;
        }
    }

    /**
     * 보안 헤더 설정
     */
    @Bean
    public SecurityHeadersConfig securityHeadersConfig() {
        return new SecurityHeadersConfig(
                "DENY",                                    // X-Frame-Options
                "nosniff",                                 // X-Content-Type-Options
                "1; mode=block",                           // X-XSS-Protection
                "max-age=31536000; includeSubDomains",    // Strict-Transport-Security
                "default-src 'self'"                       // Content-Security-Policy
        );
    }

    /**
     * 보안 헤더 설정 레코드
     */
    public record SecurityHeadersConfig(
            String frameOptions,
            String contentTypeOptions,
            String xssProtection,
            String strictTransportSecurity,
            String contentSecurityPolicy
    ) {
    }
}
