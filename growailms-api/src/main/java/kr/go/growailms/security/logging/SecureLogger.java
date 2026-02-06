package kr.go.growailms.security.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * KISA SR4-2: 보안 로깅
 *
 * 보안 이벤트 로깅 유틸리티
 * - 민감 정보 마스킹
 * - 구조화된 로그 형식
 * - 감사 추적 지원
 */
public final class SecureLogger {

    private static final Logger securityLogger = LoggerFactory.getLogger("SECURITY");
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private SecureLogger() {
        throw new AssertionError("Utility class - instantiation not allowed");
    }

    /**
     * 보안 이벤트 로깅
     */
    public static void logSecurityEvent(SecurityEventType eventType, String message, Map<String, String> context) {
        String eventId = generateEventId();
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);

        try {
            MDC.put("eventId", eventId);
            MDC.put("eventType", eventType.name());
            MDC.put("timestamp", timestamp);

            if (context != null) {
                context.forEach((key, value) -> MDC.put(key, sanitizeLogValue(value)));
            }

            String sanitizedMessage = sanitizeLogMessage(message);

            switch (eventType.getSeverity()) {
                case CRITICAL -> securityLogger.error("[{}] {} - {}", eventType.name(), eventId, sanitizedMessage);
                case HIGH -> securityLogger.warn("[{}] {} - {}", eventType.name(), eventId, sanitizedMessage);
                case MEDIUM -> securityLogger.info("[{}] {} - {}", eventType.name(), eventId, sanitizedMessage);
                case LOW -> securityLogger.debug("[{}] {} - {}", eventType.name(), eventId, sanitizedMessage);
            }
        } finally {
            MDC.clear();
        }
    }

    /**
     * 인증 이벤트 로깅
     */
    public static void logAuthenticationEvent(AuthAction action, String userId, String ipAddress, boolean success) {
        Map<String, String> context = Map.of(
                "action", action.name(),
                "userId", maskUserId(userId),
                "ipAddress", maskIpAddress(ipAddress),
                "success", String.valueOf(success)
        );

        SecurityEventType eventType = success ?
                SecurityEventType.AUTHENTICATION_SUCCESS :
                SecurityEventType.AUTHENTICATION_FAILURE;

        logSecurityEvent(eventType, action.getDescription(), context);
    }

    /**
     * 접근 제어 이벤트 로깅
     */
    public static void logAccessControlEvent(String userId, String resource, String action, boolean permitted) {
        Map<String, String> context = Map.of(
                "userId", maskUserId(userId),
                "resource", sanitizeLogValue(resource),
                "action", action,
                "permitted", String.valueOf(permitted)
        );

        SecurityEventType eventType = permitted ?
                SecurityEventType.ACCESS_GRANTED :
                SecurityEventType.ACCESS_DENIED;

        logSecurityEvent(eventType, "Access control decision", context);
    }

    /**
     * 데이터 접근 감사 로깅
     */
    public static void logDataAccess(String userId, String dataType, String operation, String recordId) {
        String eventId = generateEventId();
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);

        try {
            MDC.put("eventId", eventId);
            MDC.put("userId", maskUserId(userId));
            MDC.put("dataType", dataType);
            MDC.put("operation", operation);
            MDC.put("recordId", maskRecordId(recordId));

            auditLogger.info("[DATA_ACCESS] {} - user={}, type={}, op={}, record={}",
                    eventId, maskUserId(userId), dataType, operation, maskRecordId(recordId));
        } finally {
            MDC.clear();
        }
    }

    /**
     * 시스템 보안 이벤트 로깅
     */
    public static void logSystemSecurityEvent(String component, String event, String details) {
        Map<String, String> context = Map.of(
                "component", component,
                "event", event
        );

        logSecurityEvent(SecurityEventType.SYSTEM_SECURITY_EVENT, sanitizeLogMessage(details), context);
    }

    /**
     * 입력 검증 실패 로깅
     */
    public static void logValidationFailure(String field, String reason, String ipAddress) {
        Map<String, String> context = Map.of(
                "field", sanitizeLogValue(field),
                "reason", reason,
                "ipAddress", maskIpAddress(ipAddress)
        );

        logSecurityEvent(SecurityEventType.INPUT_VALIDATION_FAILURE, "Input validation failed", context);
    }

    /**
     * 의심스러운 활동 로깅
     */
    public static void logSuspiciousActivity(String activityType, String description, String ipAddress, String userId) {
        Map<String, String> context = Map.of(
                "activityType", activityType,
                "ipAddress", maskIpAddress(ipAddress),
                "userId", maskUserId(userId)
        );

        logSecurityEvent(SecurityEventType.SUSPICIOUS_ACTIVITY, sanitizeLogMessage(description), context);
    }

    /**
     * 로그 메시지 정제 (로그 인젝션 방지)
     */
    public static String sanitizeLogMessage(String message) {
        if (message == null) {
            return "";
        }
        return message
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replaceAll("[\\x00-\\x1F\\x7F]", "");
    }

    /**
     * 로그 값 정제
     */
    public static String sanitizeLogValue(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = sanitizeLogMessage(value);
        return sanitized.length() > 200 ? sanitized.substring(0, 200) + "..." : sanitized;
    }

    /**
     * 사용자 ID 마스킹
     */
    public static String maskUserId(String userId) {
        if (userId == null || userId.length() < 4) {
            return "***";
        }
        return userId.substring(0, 2) + "***" + userId.substring(userId.length() - 1);
    }

    /**
     * IP 주소 마스킹 (마지막 옥텟)
     */
    public static String maskIpAddress(String ipAddress) {
        if (ipAddress == null) {
            return "***";
        }
        int lastDot = ipAddress.lastIndexOf('.');
        if (lastDot > 0) {
            return ipAddress.substring(0, lastDot) + ".***";
        }
        return "***";
    }

    /**
     * 레코드 ID 마스킹
     */
    public static String maskRecordId(String recordId) {
        if (recordId == null || recordId.length() < 4) {
            return "***";
        }
        return recordId.substring(0, 2) + "***";
    }

    private static String generateEventId() {
        return UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    /**
     * 보안 이벤트 유형
     */
    public enum SecurityEventType {
        AUTHENTICATION_SUCCESS(Severity.LOW),
        AUTHENTICATION_FAILURE(Severity.MEDIUM),
        ACCESS_GRANTED(Severity.LOW),
        ACCESS_DENIED(Severity.MEDIUM),
        AUTHORIZATION_FAILURE(Severity.HIGH),
        INPUT_VALIDATION_FAILURE(Severity.MEDIUM),
        SUSPICIOUS_ACTIVITY(Severity.HIGH),
        SECURITY_VIOLATION(Severity.CRITICAL),
        SYSTEM_SECURITY_EVENT(Severity.MEDIUM),
        DATA_BREACH_ATTEMPT(Severity.CRITICAL),
        CONFIGURATION_CHANGE(Severity.MEDIUM);

        private final Severity severity;

        SecurityEventType(Severity severity) {
            this.severity = severity;
        }

        public Severity getSeverity() {
            return severity;
        }
    }

    /**
     * 심각도 레벨
     */
    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    /**
     * 인증 액션 유형
     */
    public enum AuthAction {
        LOGIN("User login attempt"),
        LOGOUT("User logout"),
        PASSWORD_CHANGE("Password change"),
        PASSWORD_RESET("Password reset request"),
        TOKEN_REFRESH("Token refresh"),
        TOKEN_REVOKE("Token revocation"),
        MFA_VERIFY("MFA verification"),
        ACCOUNT_LOCK("Account locked"),
        ACCOUNT_UNLOCK("Account unlocked");

        private final String description;

        AuthAction(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
