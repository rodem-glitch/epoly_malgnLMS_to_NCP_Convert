package kr.go.growailms.tutor.privacy;

import kr.go.growailms.tutor.common.TutorApiResponse;
import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 왜: tutor_lms/api/privacy_log.jsp를 REST 컨트롤러로 변환합니다.
 *     init.jsp 인증은 TutorAuthInterceptor가 처리하므로, 여기서는 비즈니스 로직만 담당합니다.
 *
 * 엔드포인트 매핑:
 *   privacy_log.jsp → POST /api/tutor/privacy/log
 *
 * 왜: 교수자 LMS에서 개인정보 조회/다운로드 사유를 기록하기 위해 별도 로그를 남깁니다.
 */
@RestController
@RequestMapping("/api/tutor/privacy")
public class TutorPrivacyController {

    private static final Logger log = LoggerFactory.getLogger(TutorPrivacyController.class);

    private final TutorPrivacyService privacyService;

    public TutorPrivacyController(TutorPrivacyService privacyService) {
        this.privacyService = privacyService;
    }

    // ========== privacy_log.jsp ==========
    @PostMapping("/log")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> writePrivacyLog(
            @RequestParam(name = "log_type", defaultValue = "V") String logType,
            @RequestParam(name = "purpose") String purpose,
            @RequestParam(name = "page_nm", defaultValue = "수강생 정보") String pageNm,
            @RequestParam(name = "course_id", defaultValue = "0") int courseId,
            @RequestParam(name = "user_ids", defaultValue = "") String userIds,
            @RequestParam(name = "user_cnt", defaultValue = "0") int userCnt
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("개인정보 로그 기록: userId={}, logType={}, courseId={}", auth.userId(), logType, courseId);

        return privacyService.writeLog(auth, logType, purpose, pageNm, courseId, userIds, userCnt)
                .map(data -> ResponseEntity.ok(TutorApiResponse.success(data)))
                .orElse(ResponseEntity.ok(TutorApiResponse.error(
                        TutorApiResponse.CODE_NO_EDIT_PERMISSION, "개인정보 로그 기록 권한이 없습니다.")));
    }
}
