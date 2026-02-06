package kr.go.growailms.tutor.progress;

import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 왜: progress_*.jsp 3개의 비즈니스 로직을 모아둔 서비스입니다.
 *     진도 관리는 LM_COURSE_PROGRESS(레슨별 진도), LM_COURSE_LESSON(커리큘럼),
 *     LM_COURSE_USER(수강 정보) 세 테이블을 조합하여 처리합니다.
 */
@Service
public class TutorProgressService {

    private static final Logger log = LoggerFactory.getLogger(TutorProgressService.class);

    private final TutorProgressJdbcRepository repo;

    public TutorProgressService(TutorProgressJdbcRepository repo) {
        this.repo = repo;
    }

    // ========== 권한 확인 공통 메서드 ==========

    private boolean canAccessCourse(TutorAuthContext.AuthInfo auth, int courseId) {
        // 왜: 관리자는 전체 과목, 교수자는 주담당 과목만 접근 가능합니다.
        return auth.isAdmin() || repo.isMajorTutor(auth.userId(), courseId, auth.siteId());
    }

    // ========== progress_summary.jsp ==========

    public Optional<Map<String, Object>> getProgressSummary(TutorAuthContext.AuthInfo auth, int courseId) {
        if (!canAccessCourse(auth, courseId)) {
            return Optional.empty();
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("course_id", courseId);

        // 왜: 전체 수강생 수, 수료/미수료/미진행 수를 집계합니다.
        Map<String, Object> stats = repo.getProgressStats(courseId, auth.siteId());
        summary.put("total_students", stats.getOrDefault("total_students", 0));
        summary.put("completed_cnt", stats.getOrDefault("completed_cnt", 0));
        summary.put("in_progress_cnt", stats.getOrDefault("in_progress_cnt", 0));
        summary.put("not_started_cnt", stats.getOrDefault("not_started_cnt", 0));

        // 왜: 평균 진도율을 계산합니다.
        summary.put("avg_progress", stats.getOrDefault("avg_progress", 0));

        // 왜: 레슨별 진도 요약(각 레슨의 완료율)을 반환합니다.
        List<Map<String, Object>> lessonStats = repo.getLessonProgressStats(courseId, auth.siteId());
        summary.put("lesson_stats", lessonStats);

        return Optional.of(summary);
    }

    // ========== progress_students.jsp ==========

    public List<Map<String, Object>> listProgressStudents(TutorAuthContext.AuthInfo auth, int courseId,
                                                           String keyword, String completeStatus,
                                                           int page, int pageSize) {
        if (!canAccessCourse(auth, courseId)) {
            return List.of();
        }

        List<Map<String, Object>> students = repo.listProgressStudents(courseId, auth.siteId(),
                keyword, completeStatus, page, pageSize);

        // 왜: JSP에서 하던 화면용 가공(수료 상태 라벨, 진도율 포맷)을 수행합니다.
        for (Map<String, Object> student : students) {
            formatStudentProgress(student);
        }
        return students;
    }

    // ========== progress_detail.jsp ==========

    public Optional<Map<String, Object>> getProgressDetail(TutorAuthContext.AuthInfo auth,
                                                            int courseId, int userId) {
        if (!canAccessCourse(auth, courseId)) {
            return Optional.empty();
        }

        // 왜: 수강생의 전체 진도 정보를 가져옵니다.
        Optional<Map<String, Object>> courseUserOpt = repo.getCourseUser(courseId, userId, auth.siteId());
        if (courseUserOpt.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> result = new LinkedHashMap<>(courseUserOpt.get());
        formatStudentProgress(result);

        // 왜: 레슨별 진도 상세를 가져옵니다.
        List<Map<String, Object>> lessonProgress = repo.getLessonProgress(courseId, userId, auth.siteId());
        for (Map<String, Object> lp : lessonProgress) {
            // 왜: 진도율을 퍼센트로 표시합니다.
            Object ratio = lp.get("progress_ratio");
            if (ratio != null) {
                lp.put("progress_pct", ((Number) ratio).intValue() + "%");
            } else {
                lp.put("progress_pct", "0%");
            }
        }
        result.put("lesson_progress", lessonProgress);

        return Optional.of(result);
    }

    // ==================== 내부 유틸리티 ====================

    private void formatStudentProgress(Map<String, Object> row) {
        // 왜: 수료 상태 라벨
        String completeYn = str(row, "complete_yn");
        row.put("complete_conv", "Y".equals(completeYn) ? "수료" : "미수료");

        // 왜: 진도율 포맷
        Object progressRatio = row.get("progress_ratio");
        if (progressRatio != null) {
            row.put("progress_pct", ((Number) progressRatio).intValue() + "%");
        } else {
            row.put("progress_pct", "0%");
        }

        // 왜: 총점 포맷
        Object totalScore = row.get("total_score");
        if (totalScore != null) {
            row.put("total_score_conv", ((Number) totalScore).intValue() + "점");
        } else {
            row.put("total_score_conv", "0점");
        }
    }

    private String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v != null ? v.toString().trim() : "";
    }
}
