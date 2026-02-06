package kr.go.growailms.tutor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.go.growailms.tutor.common.TutorApiResponse;
import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Optional;

/**
 * 왜: init.jsp가 하던 3단계 인증을 Spring Boot 인터셉터로 대체합니다.
 *
 * init.jsp 인증 흐름:
 *   1) 로그인 확인 (userId == 0 → 4010)
 *   2) 사용자 존재/활성 확인 (TB_USER status=1 → 4041)
 *   3) 관리자(S/A) 또는 교수자(tutor_yn=Y) 확인 (→ 4030)
 *
 * 인증 정보 전달 방식:
 *   - 헤더: X-Tutor-User-Id, X-Tutor-Site-Id (프록시/게이트웨이가 세션에서 추출하여 전달)
 *   - 이 값을 TB_USER에서 검증한 뒤 TutorAuthContext에 저장합니다.
 */
@Component
public class TutorAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TutorAuthInterceptor.class);

    // 왜: 프록시(레거시 JSP 또는 API Gateway)가 세션에서 추출한 userId/siteId를 이 헤더로 전달합니다.
    private static final String HEADER_USER_ID = "X-Tutor-User-Id";
    private static final String HEADER_SITE_ID = "X-Tutor-Site-Id";

    private final TutorUserJdbcRepository userRepository;
    private final ObjectMapper objectMapper;

    public TutorAuthInterceptor(TutorUserJdbcRepository userRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 왜: init.jsp와 동일하게 캐시를 차단하여, SPA 호출 시 이전 응답이 남지 않게 합니다.
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        // 1단계: 로그인 확인 (init.jsp: userId == 0 → 4010)
        long userId = parseLongHeader(request, HEADER_USER_ID);
        long siteId = parseLongHeader(request, HEADER_SITE_ID);

        if (userId <= 0) {
            log.warn("교수자 API 인증 실패: userId 누락 (path={})", request.getRequestURI());
            writeJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED, TutorApiResponse.loginRequired());
            return false;
        }
        if (siteId <= 0) {
            log.warn("교수자 API 인증 실패: siteId 누락 (path={}, userId={})", request.getRequestURI(), userId);
            writeJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    TutorApiResponse.error(TutorApiResponse.CODE_MISSING_PARAM, "사이트 정보가 필요합니다."));
            return false;
        }

        // 2단계: 사용자 존재/활성 확인 (init.jsp: TB_USER status=1 → 4041)
        Optional<TutorUserJdbcRepository.TutorUserInfo> userOpt = userRepository.findActiveUser(userId, siteId);
        if (userOpt.isEmpty()) {
            log.warn("교수자 API 인증 실패: 사용자 없음 (userId={}, siteId={})", userId, siteId);
            writeJsonResponse(response, HttpServletResponse.SC_FORBIDDEN,
                    TutorApiResponse.error(TutorApiResponse.CODE_USER_NOT_FOUND, "사용자 정보가 없습니다."));
            return false;
        }

        TutorUserJdbcRepository.TutorUserInfo user = userOpt.get();
        boolean isAdmin = "S".equals(user.userKind()) || "A".equals(user.userKind());

        // 3단계: 관리자(S/A) 또는 교수자(tutor_yn=Y) 확인 (init.jsp → 4030)
        if (!isAdmin && !user.isTutor()) {
            log.warn("교수자 API 인증 실패: 교수자 권한 없음 (userId={}, userKind={})", userId, user.userKind());
            writeJsonResponse(response, HttpServletResponse.SC_FORBIDDEN, TutorApiResponse.noPermission());
            return false;
        }

        // 왜: 인증 통과 후 TutorAuthContext에 저장하여, 컨트롤러/서비스에서 꺼내 씁니다.
        TutorAuthContext.set(new TutorAuthContext.AuthInfo(userId, siteId, user.userKind(), isAdmin));

        log.debug("교수자 API 인증 성공: userId={}, siteId={}, userKind={}, isAdmin={}",
                userId, siteId, user.userKind(), isAdmin);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 왜: ThreadLocal 누수 방지를 위해 요청 완료 후 반드시 정리합니다.
        TutorAuthContext.clear();
    }

    private long parseLongHeader(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("교수자 API 헤더 파싱 실패: {}={}", headerName, value);
            return 0;
        }
    }

    private void writeJsonResponse(HttpServletResponse response, int httpStatus, TutorApiResponse<?> body)
            throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
