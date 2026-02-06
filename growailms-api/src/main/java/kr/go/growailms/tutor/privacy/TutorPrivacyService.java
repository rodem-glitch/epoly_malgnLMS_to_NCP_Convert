package kr.go.growailms.tutor.privacy;

import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 왜: privacy_log.jsp의 비즈니스 로직을 모아둔 서비스입니다.
 *     교수자가 개인정보를 조회/다운로드할 때 사유 로그를 남깁니다.
 *     테이블: TB_INFO_LOG, LM_COURSE_TUTOR
 */
@Service
public class TutorPrivacyService {

    private static final Logger log = LoggerFactory.getLogger(TutorPrivacyService.class);

    private final TutorPrivacyJdbcRepository repo;

    public TutorPrivacyService(TutorPrivacyJdbcRepository repo) {
        this.repo = repo;
    }

    // ========== privacy_log.jsp ==========

    public Optional<Map<String, Object>> writeLog(TutorAuthContext.AuthInfo auth, String logType,
                                                    String purpose, String pageNm, int courseId,
                                                    String userIds, int userCnt) {
        // 왜: 사유가 비어있으면 기록 불가
        if (purpose == null || purpose.trim().isEmpty()) {
            return Optional.empty();
        }

        // 왜: 과목이 지정된 경우, 관리자가 아니면 주담당 교수자인지 확인
        if (courseId > 0 && !auth.isAdmin()) {
            if (!repo.isMajorTutor(auth.userId(), courseId, auth.siteId())) {
                return Optional.empty();
            }
        }

        // 왜: logType = V(조회), E(엑셀다운로드)
        if (!"E".equals(logType)) {
            logType = "V";
        }

        long logId = repo.insertPrivacyLog(auth.siteId(), auth.userId(), logType, purpose,
                pageNm, courseId, userIds, userCnt);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("log_id", logId);
        result.put("log_type", logType);
        return Optional.of(result);
    }
}
