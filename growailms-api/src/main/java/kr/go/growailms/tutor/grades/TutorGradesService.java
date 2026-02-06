package kr.go.growailms.tutor.grades;

import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 왜: grades_list.jsp, grades_recalc.jsp의 비즈니스 로직을 모아둔 서비스입니다.
 *     과목별 수강생 성적(진도/시험/과제/총점) 조회 및 재계산을 담당합니다.
 *     테이블: LM_COURSE_USER, LM_COURSE, TB_USER
 */
@Service
public class TutorGradesService {

    private static final Logger log = LoggerFactory.getLogger(TutorGradesService.class);

    private final TutorGradesJdbcRepository repo;

    public TutorGradesService(TutorGradesJdbcRepository repo) {
        this.repo = repo;
    }

    // ========== grades_list.jsp ==========

    public List<Map<String, Object>> listGrades(TutorAuthContext.AuthInfo auth,
                                                 int courseId, String keyword) {
        // 왜: 관리자는 전체, 교수자는 주담당만
        if (!auth.isAdmin() && !repo.isMajorTutor(auth.userId(), courseId, auth.siteId())) {
            log.warn("성적 목록 권한 없음: userId={}, courseId={}", auth.userId(), courseId);
            return Collections.emptyList();
        }

        return repo.listGrades(courseId, auth.siteId(), keyword);
    }

    // ========== grades_recalc.jsp ==========

    public Optional<Map<String, Object>> recalcGrades(TutorAuthContext.AuthInfo auth,
                                                       int courseId, int courseUserId) {
        if (!auth.isAdmin() && !repo.isMajorTutor(auth.userId(), courseId, auth.siteId())) {
            return Optional.empty();
        }

        // 왜: courseUserId = 0이면 과목 전체, 아니면 해당 수강생만 재계산
        int updated = repo.recalcGrades(courseId, auth.siteId(), courseUserId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("course_id", courseId);
        result.put("updated_count", updated);
        result.put("target", courseUserId > 0 ? "single" : "all");
        return Optional.of(result);
    }
}
