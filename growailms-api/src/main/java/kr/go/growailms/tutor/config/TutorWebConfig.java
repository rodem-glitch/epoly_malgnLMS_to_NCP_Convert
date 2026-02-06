package kr.go.growailms.tutor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 왜: /api/tutor/** 경로로 들어오는 모든 요청에 TutorAuthInterceptor를 적용합니다.
 *     레거시 JSP에서 <%@ include file="init.jsp" %>로 매 파일마다 인증을 포함하던 것을
 *     인터셉터 하나로 자동 적용합니다.
 */
@Configuration
public class TutorWebConfig implements WebMvcConfigurer {

    private final TutorAuthInterceptor tutorAuthInterceptor;

    public TutorWebConfig(TutorAuthInterceptor tutorAuthInterceptor) {
        this.tutorAuthInterceptor = tutorAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tutorAuthInterceptor)
                .addPathPatterns("/api/tutor/**");
    }
}
