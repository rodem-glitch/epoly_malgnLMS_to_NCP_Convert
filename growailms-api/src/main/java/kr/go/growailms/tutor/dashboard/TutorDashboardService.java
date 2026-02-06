package kr.go.growailms.tutor.dashboard;

import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 왜: dashboard.jsp의 비즈니스 로직을 모아둔 서비스입니다.
 *     대시보드는 여러 테이블(LM_COURSE, LM_COURSE_USER, LM_HOMEWORK_USER, CL_POST)에서
 *     데이터를 집계하여 통계 카드, 활성 과목 Top 5, 최근 제출물, 최근 QnA를 반환합니다.
 */
@Service
public class TutorDashboardService {

    private static final Logger log = LoggerFactory.getLogger(TutorDashboardService.class);

    private final TutorDashboardJdbcRepository repo;

    public TutorDashboardService(TutorDashboardJdbcRepository repo) {
        this.repo = repo;
    }

    public Map<String, Object> getDashboard(TutorAuthContext.AuthInfo auth) {
        Map<String, Object> dashboard = new LinkedHashMap<>();

        // 왜: 통계 카드 4종을 집계합니다.
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_courses", repo.countCourses(auth.userId(), auth.siteId(), auth.isAdmin()));
        stats.put("total_students", repo.countStudents(auth.userId(), auth.siteId(), auth.isAdmin()));
        stats.put("unanswered_qna", repo.countUnansweredQna(auth.userId(), auth.siteId(), auth.isAdmin()));
        stats.put("recent_submissions", repo.countRecentSubmissions(auth.userId(), auth.siteId(), auth.isAdmin()));
        dashboard.put("stats", stats);

        // 왜: 활성 과목 Top 5 (수강생 수 기준)
        List<Map<String, Object>> topCourses = repo.listTopActiveCourses(
                auth.userId(), auth.siteId(), auth.isAdmin(), 5);
        dashboard.put("top_courses", topCourses);

        // 왜: 최근 과제 제출물 5건
        List<Map<String, Object>> recentSubmissions = repo.listRecentSubmissions(
                auth.userId(), auth.siteId(), auth.isAdmin(), 5);
        dashboard.put("recent_submissions", recentSubmissions);

        // 왜: 최근 QnA 5건
        List<Map<String, Object>> recentQna = repo.listRecentQna(
                auth.userId(), auth.siteId(), auth.isAdmin(), 5);
        // 왜: QnA 답변 상태 라벨을 추가합니다.
        for (Map<String, Object> qna : recentQna) {
            Object answerContent = qna.get("answer_content");
            qna.put("answer_status_conv",
                    (answerContent != null && !answerContent.toString().trim().isEmpty()) ? "답변완료" : "미답변");
        }
        dashboard.put("recent_qna", recentQna);

        log.debug("대시보드 데이터 조회 완료: userId={}, courses={}, students={}",
                auth.userId(), stats.get("total_courses"), stats.get("total_students"));
        return dashboard;
    }
}
