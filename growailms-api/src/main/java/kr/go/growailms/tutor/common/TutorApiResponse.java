package kr.go.growailms.tutor.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 왜: 레거시 JSP API(init.jsp → Json result)의 응답 포맷을 그대로 유지하여
 *     프론트(React)를 수정 없이 Spring Boot로 전환할 수 있게 합니다.
 *
 * JSP 응답 예시:
 *   { "rst_code": "0000", "rst_message": "성공", "rst_count": 5, "rst_data": [...] }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TutorApiResponse<T>(
        @JsonProperty("rst_code") String rstCode,
        @JsonProperty("rst_message") String rstMessage,
        @JsonProperty("rst_count") Integer rstCount,
        @JsonProperty("rst_data") T rstData
) {

    // 왜: JSP 응답 코드 체계를 상수로 정의하여 오타를 방지합니다.
    public static final String CODE_SUCCESS = "0000";
    public static final String CODE_MISSING_REQUIRED = "1000";
    public static final String CODE_MISSING_PARAM = "1001";
    public static final String CODE_BUSINESS_ERROR = "1100";
    public static final String CODE_DB_FAIL = "2000";
    public static final String CODE_LOGIN_REQUIRED = "4010";
    public static final String CODE_NO_PERMISSION = "4030";
    public static final String CODE_NO_EDIT_PERMISSION = "4031";
    public static final String CODE_NOT_FOUND = "4040";
    public static final String CODE_USER_NOT_FOUND = "4041";
    public static final String CODE_METHOD_NOT_ALLOWED = "4050";
    public static final String CODE_INVALID_ACCESS = "9999";

    // --- 팩토리 메서드 ---

    public static <T> TutorApiResponse<T> success(T data) {
        return new TutorApiResponse<>(CODE_SUCCESS, "성공", null, data);
    }

    public static <T> TutorApiResponse<T> success(T data, int count) {
        return new TutorApiResponse<>(CODE_SUCCESS, "성공", count, data);
    }

    public static TutorApiResponse<Void> success() {
        return new TutorApiResponse<>(CODE_SUCCESS, "성공", null, null);
    }

    // 왜: 에러 응답은 rst_data가 null이므로 제네릭 타입을 호출부에 맞춰줍니다.
    @SuppressWarnings("unchecked")
    public static <T> TutorApiResponse<T> error(String code, String message) {
        return new TutorApiResponse<>(code, message, null, null);
    }

    public static <T> TutorApiResponse<T> loginRequired() {
        return error(CODE_LOGIN_REQUIRED, "로그인이 필요합니다.");
    }

    public static <T> TutorApiResponse<T> noPermission() {
        return error(CODE_NO_PERMISSION, "교수자 권한이 없습니다.");
    }

    public static <T> TutorApiResponse<T> notFound(String target) {
        return error(CODE_NOT_FOUND, target + "을(를) 찾을 수 없습니다.");
    }

    public static <T> TutorApiResponse<T> missingParam(String paramName) {
        return error(CODE_MISSING_PARAM, paramName + " 값이 필요합니다.");
    }
}
