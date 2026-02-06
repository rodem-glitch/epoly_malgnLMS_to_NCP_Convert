package kr.go.growailms.tutor.certificate;

import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 왜: certificate_templates.jsp, certificate_issue.jsp의 비즈니스 로직을 모아둔 서비스입니다.
 *     증명서 템플릿 조회 및 수료증/합격증 인쇄 URL 발급을 담당합니다.
 *     테이블: LM_CERTIFICATE_TEMPLATE, LM_COURSE_USER, LM_COURSE_TUTOR
 */
@Service
public class TutorCertificateService {

    private static final Logger log = LoggerFactory.getLogger(TutorCertificateService.class);

    private final TutorCertificateJdbcRepository repo;

    public TutorCertificateService(TutorCertificateJdbcRepository repo) {
        this.repo = repo;
    }

    // ========== certificate_templates.jsp ==========

    public List<Map<String, Object>> listTemplates(TutorAuthContext.AuthInfo auth, String templateType) {
        // 왜: 환경마다 template_type 컬럼 존재 여부가 다를 수 있어, 실패하면 전체로 fallback
        try {
            return repo.listTemplates(auth.siteId(), templateType);
        } catch (Exception e) {
            log.warn("템플릿 목록 조회 실패(template_type 필터), 전체 조회로 fallback: {}", e.getMessage());
            return repo.listTemplates(auth.siteId(), "");
        }
    }

    // ========== certificate_issue.jsp ==========

    public Optional<Map<String, Object>> issueCertificate(TutorAuthContext.AuthInfo auth,
                                                           int courseUserId, String certType) {
        // 왜: P(합격) 아니면 기본 C(수료)
        if (!"P".equals(certType)) {
            certType = "C";
        }

        // 왜: 수강 정보 확인
        Optional<Map<String, Object>> courseUserOpt = repo.findCourseUser(courseUserId, auth.siteId());
        if (courseUserOpt.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> courseUser = courseUserOpt.get();
        int courseId = ((Number) courseUser.get("course_id")).intValue();

        // 왜: 관리자이거나 주담당 교수자만 발급 가능
        if (!auth.isAdmin() && !repo.isMajorTutor(auth.userId(), courseId, auth.siteId())) {
            return Optional.empty();
        }

        // 왜: 인쇄용 URL을 생성하여 반환 — 레거시와 동일한 경로
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("course_user_id", courseUserId);
        result.put("type", certType);
        result.put("print_url", "/tutor_lms/certificate_template.jsp?cuid=" + courseUserId + "&type=" + certType);

        return Optional.of(result);
    }
}
