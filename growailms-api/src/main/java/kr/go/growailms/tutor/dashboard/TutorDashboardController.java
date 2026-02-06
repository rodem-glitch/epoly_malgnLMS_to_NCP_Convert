package kr.go.growailms.tutor.dashboard;

import kr.go.growailms.tutor.common.TutorApiResponse;
import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 왜: tutor_lms/dashboard.jsp를 REST 컨트롤러로 변환합니다.
 *     init.jsp 인증은 TutorAuthInterceptor가 처리하므로, 여기서는 비즈니스 로직만 담당합니다.
 *
 * 엔드포인트 매핑:
 *   dashboard.jsp → GET /api/tutor/dashboard
 *
 * 반환 데이터:
 *   - stats: 통계 카드 (전체 과목 수, 전체 수강생 수, 미답변 QnA 수, 최근 제출 수)
 *   - top_courses: 활성 과목 Top 5 (수강생 수 기준)
 *   - recent_submissions: 최근 과제 제출물 5건
 *   - recent_qna: 최근 QnA 5건
 */
@RestController
@RequestMapping("/api/tutor/dashboard")
public class TutorDashboardController {

    private static final Logger log = LoggerFactory.getLogger(TutorDashboardController.class);

    private final TutorDashboardService dashboardService;

    public TutorDashboardController(TutorDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    // ========== dashboard.jsp ==========
    @GetMapping
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> getDashboard() {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("대시보드 조회: userId={}", auth.userId());

        Map<String, Object> dashboard = dashboardService.getDashboard(auth);
        return ResponseEntity.ok(TutorApiResponse.success(dashboard));
    }
}
