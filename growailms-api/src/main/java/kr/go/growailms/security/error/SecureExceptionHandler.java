package kr.go.growailms.security.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * KISA SR4-1: 에러 처리
 *
 * 전역 예외 처리 핸들러
 * - 내부 에러 정보 은닉
 * - 사용자 친화적 에러 메시지
 * - 보안 로깅
 */
@RestControllerAdvice
public class SecureExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(SecureExceptionHandler.class);

    private static final String GENERIC_ERROR_MESSAGE = "요청을 처리하는 중 오류가 발생했습니다.";
    private static final String VALIDATION_ERROR_MESSAGE = "입력값이 올바르지 않습니다.";
    private static final String UNAUTHORIZED_MESSAGE = "인증이 필요합니다.";
    private static final String FORBIDDEN_MESSAGE = "접근 권한이 없습니다.";
    private static final String NOT_FOUND_MESSAGE = "요청한 리소스를 찾을 수 없습니다.";

    /**
     * 일반적인 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logError(errorId, ex, request);

        ErrorResponse response = ErrorResponse.builder()
                .errorId(errorId)
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message(GENERIC_ERROR_MESSAGE)
                .path(sanitizePath(request.getRequestURI()))
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 유효성 검사 예외 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String errorId = generateErrorId();
        logger.warn("Validation error [{}]: {}", errorId, ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .errorId(errorId)
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(VALIDATION_ERROR_MESSAGE)
                .path(sanitizePath(request.getRequestURI()))
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 파라미터 누락 예외 처리
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        String errorId = generateErrorId();
        logger.warn("Missing parameter [{}]: {}", errorId, ex.getParameterName());

        ErrorResponse response = ErrorResponse.builder()
                .errorId(errorId)
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("필수 파라미터가 누락되었습니다.")
                .path(sanitizePath(request.getRequestURI()))
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 파라미터 타입 불일치 예외 처리
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String errorId = generateErrorId();
        logger.warn("Type mismatch [{}]: parameter={}", errorId, ex.getName());

        ErrorResponse response = ErrorResponse.builder()
                .errorId(errorId)
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("파라미터 형식이 올바르지 않습니다.")
                .path(sanitizePath(request.getRequestURI()))
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 비즈니스 로직 예외 처리
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {

        String errorId = generateErrorId();
        logger.warn("Business error [{}]: code={}", errorId, ex.getErrorCode());

        ErrorResponse response = ErrorResponse.builder()
                .errorId(errorId)
                .status(ex.getHttpStatus().value())
                .error(ex.getHttpStatus().getReasonPhrase())
                .message(ex.getUserMessage())
                .path(sanitizePath(request.getRequestURI()))
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    /**
     * 보안 예외 처리
     */
    @ExceptionHandler(SecurityViolationException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(
            SecurityViolationException ex, HttpServletRequest request) {

        String errorId = generateErrorId();
        // 보안 예외는 상세 로깅
        logger.error("Security violation [{}]: type={}, ip={}, uri={}",
                errorId, ex.getViolationType(),
                getClientIp(request), sanitizePath(request.getRequestURI()));

        ErrorResponse response = ErrorResponse.builder()
                .errorId(errorId)
                .status(HttpStatus.FORBIDDEN.value())
                .error(HttpStatus.FORBIDDEN.getReasonPhrase())
                .message(FORBIDDEN_MESSAGE)
                .path(sanitizePath(request.getRequestURI()))
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * 인증 예외 처리
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {

        String errorId = generateErrorId();
        logger.warn("Authentication failure [{}]: reason={}", errorId, ex.getReason());

        ErrorResponse response = ErrorResponse.builder()
                .errorId(errorId)
                .status(HttpStatus.UNAUTHORIZED.value())
                .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                .message(UNAUTHORIZED_MESSAGE)
                .path(sanitizePath(request.getRequestURI()))
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * 리소스 미발견 예외 처리
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {

        String errorId = generateErrorId();
        logger.info("Resource not found [{}]: type={}", errorId, ex.getResourceType());

        ErrorResponse response = ErrorResponse.builder()
                .errorId(errorId)
                .status(HttpStatus.NOT_FOUND.value())
                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                .message(NOT_FOUND_MESSAGE)
                .path(sanitizePath(request.getRequestURI()))
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    private void logError(String errorId, Exception ex, HttpServletRequest request) {
        // 민감 정보를 제외하고 로깅
        logger.error("Unhandled exception [{}]: type={}, uri={}, ip={}",
                errorId,
                ex.getClass().getSimpleName(),
                sanitizePath(request.getRequestURI()),
                getClientIp(request));
        logger.debug("Exception details [{}]:", errorId, ex);
    }

    private String generateErrorId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String sanitizePath(String path) {
        if (path == null) {
            return "";
        }
        // 경로에서 잠재적 위험 문자 제거
        return path.replaceAll("[<>\"'&]", "")
                .replaceAll("\\.\\.+", "")
                .substring(0, Math.min(path.length(), 200));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 비즈니스 예외 기본 클래스
     */
    public static class BusinessException extends RuntimeException {
        private final String errorCode;
        private final String userMessage;
        private final HttpStatus httpStatus;

        public BusinessException(String errorCode, String userMessage, HttpStatus httpStatus) {
            super(userMessage);
            this.errorCode = errorCode;
            this.userMessage = userMessage;
            this.httpStatus = httpStatus;
        }

        public String getErrorCode() { return errorCode; }
        public String getUserMessage() { return userMessage; }
        public HttpStatus getHttpStatus() { return httpStatus; }
    }

    /**
     * 보안 위반 예외
     */
    public static class SecurityViolationException extends RuntimeException {
        private final String violationType;

        public SecurityViolationException(String violationType, String message) {
            super(message);
            this.violationType = violationType;
        }

        public String getViolationType() { return violationType; }
    }

    /**
     * 인증 예외
     */
    public static class AuthenticationException extends RuntimeException {
        private final String reason;

        public AuthenticationException(String reason) {
            super(reason);
            this.reason = reason;
        }

        public String getReason() { return reason; }
    }

    /**
     * 리소스 미발견 예외
     */
    public static class ResourceNotFoundException extends RuntimeException {
        private final String resourceType;

        public ResourceNotFoundException(String resourceType, String message) {
            super(message);
            this.resourceType = resourceType;
        }

        public String getResourceType() { return resourceType; }
    }
}
