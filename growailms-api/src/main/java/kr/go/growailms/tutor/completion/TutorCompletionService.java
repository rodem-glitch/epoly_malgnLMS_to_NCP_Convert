package kr.go.growailms.tutor.completion;

import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 왜: completion_list.jsp, completion_update.jsp의 비즈니스 로직을 모아둔 서비스입니다.
 *     과목별 수강생의 수료/합격 상태 조회 및 일괄 변경을 담당합니다.
 *     테이블: LM_COURSE_USER, LM_COURSE, TB_USER
 */
@Service
public class TutorCompletionService {

    private static final Logger log = LoggerFactory.getLogger(TutorCompletionService.class);

    private final TutorCompletionJdbcRepository repo;

    public TutorCompletionService(TutorCompletionJdbcRepository repo) {
        this.repo = repo;
    }

    // ========== completion_list.jsp ==========

    public List<Map<String, Object>> listCompletions(TutorAuthContext.AuthInfo auth,
                                                      int courseId, String keyword, String completeStatus) {
        // 왜: 관리자는 전체 과목, 교수자는 주담당 과목만 조회 가능
        if (!auth.isAdmin() && !repo.isMajorTutor(auth.userId(), courseId, auth.siteId())) {
            log.warn("수료 목록 권한 없음: userId={}, courseId={}", auth.userId(), courseId);
            return Collections.emptyList();
        }

        return repo.listCompletions(courseId, auth.siteId(), keyword, completeStatus);
    }

    // ========== completion_update.jsp ==========

    public Map<String, Object> updateCompletion(TutorAuthContext.AuthInfo auth,
                                                 int courseId, String action, String courseUserIds) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 왜: 권한 체크 — 관리자이거나 해당 과목의 주담당 교수자만 수료 처리 가능
        if (!auth.isAdmin() && !repo.isMajorTutor(auth.userId(), courseId, auth.siteId())) {
            result.put("code", "4031");
            result.put("error", "해당 과목의 수료정보를 수정할 권한이 없습니다.");
            return result;
        }

        // 왜: course_user_ids는 콤마 구분 숫자 문자열. SQL 인젝션 방지를 위해 형식 검증
        if (!courseUserIds.matches("^[0-9,]+$")) {
            result.put("code", "1002");
            result.put("error", "course_user_ids 형식이 올바르지 않습니다.");
            return result;
        }

        int updated = repo.updateCompletion(courseId, auth.siteId(), action, courseUserIds);
        result.put("updated_count", updated);
        result.put("action", action);
        return result;
    }
}
