package kr.go.growailms.security.error;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * KISA SR4-1: 에러 처리
 *
 * 표준화된 에러 응답 DTO
 * - 내부 정보 은닉
 * - 추적 가능한 에러 ID
 * - 일관된 응답 형식
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final String errorId;
    private final int status;
    private final String error;
    private final String message;
    private final String path;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime timestamp;

    private ErrorResponse(Builder builder) {
        this.errorId = builder.errorId;
        this.status = builder.status;
        this.error = builder.error;
        this.message = builder.message;
        this.path = builder.path;
        this.timestamp = builder.timestamp;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getErrorId() {
        return errorId;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public static class Builder {
        private String errorId;
        private int status;
        private String error;
        private String message;
        private String path;
        private LocalDateTime timestamp;

        public Builder errorId(String errorId) {
            this.errorId = errorId;
            return this;
        }

        public Builder status(int status) {
            this.status = status;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ErrorResponse build() {
            return new ErrorResponse(this);
        }
    }
}
