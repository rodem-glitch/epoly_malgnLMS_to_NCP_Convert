package kr.go.growailms.tutor.certificate;

import kr.go.growailms.tutor.common.TutorApiResponse;
import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 왜: tutor_lms/api/certificate_*.jsp 2개를 하나의 REST 컨트롤러로 통합합니다.
 *     init.jsp 인증은 TutorAuthInterceptor가 처리하므로, 여기서는 비즈니스 로직만 담당합니다.
 *
 * 엔드포인트 매핑:
 *   certificate_templates.jsp → GET  /api/tutor/certificates/templates
 *   certificate_issue.jsp     → POST /api/tutor/certificates/issue
 */
@RestController
@RequestMapping("/api/tutor/certificates")
public class TutorCertificateController {

    private static final Logger log = LoggerFactory.getLogger(TutorCertificateController.class);

    private final TutorCertificateService certificateService;

    public TutorCertificateController(TutorCertificateService certificateService) {
        this.certificateService = certificateService;
    }

    // ========== certificate_templates.jsp ==========
    @GetMapping("/templates")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listTemplates(
            @RequestParam(name = "template_type", defaultValue = "") String templateType
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("증명서 템플릿 목록: userId={}, templateType={}", auth.userId(), templateType);

        List<Map<String, Object>> list = certificateService.listTemplates(auth, templateType);
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }

    // ========== certificate_issue.jsp ==========
    @PostMapping("/issue")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> issueCertificate(
            @RequestParam(name = "course_user_id") int courseUserId,
            @RequestParam(name = "type", defaultValue = "C") String certType
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("증명서 발급: userId={}, courseUserId={}, type={}", auth.userId(), courseUserId, certType);

        return certificateService.issueCertificate(auth, courseUserId, certType)
                .map(data -> ResponseEntity.ok(TutorApiResponse.success(data)))
                .orElse(ResponseEntity.ok(TutorApiResponse.error(
                        TutorApiResponse.CODE_NO_EDIT_PERMISSION, "증명서 발급 권한이 없습니다.")));
    }
}
