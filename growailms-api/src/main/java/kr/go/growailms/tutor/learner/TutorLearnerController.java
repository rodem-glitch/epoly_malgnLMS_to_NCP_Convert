package kr.go.growailms.tutor.learner;

import kr.go.growailms.tutor.common.TutorApiResponse;
import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 왜: tutor_lms/api/learner_list.jsp를 REST 컨트롤러로 변환합니다.
 *     init.jsp 인증은 TutorAuthInterceptor가 처리하므로, 여기서는 비즈니스 로직만 담당합니다.
 *
 * 엔드포인트 매핑:
 *   learner_list.jsp → GET /api/tutor/learners
 *
 * 왜: 과목개설(CreateSubjectWizard)에서 "학습자 선택" 시 실제 회원(TB_USER)을 검색합니다.
 */
@RestController
@RequestMapping("/api/tutor/learners")
public class TutorLearnerController {

    private static final Logger log = LoggerFactory.getLogger(TutorLearnerController.class);

    private final TutorLearnerService learnerService;

    public TutorLearnerController(TutorLearnerService learnerService) {
        this.learnerService = learnerService;
    }

    // ========== learner_list.jsp ==========
    @GetMapping
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listLearners(
            @RequestParam(name = "s_keyword", defaultValue = "") String keyword,
            @RequestParam(name = "s_dept", defaultValue = "") String deptKeyword,
            @RequestParam(name = "dept_id", defaultValue = "0") int deptId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("학습자 목록 검색: userId={}, keyword={}, deptId={}", auth.userId(), keyword, deptId);

        // 왜: limit 상한을 200으로 제한 — DB/서버 부담 방지
        if (limit <= 0) limit = 50;
        if (limit > 200) limit = 200;

        List<Map<String, Object>> list = learnerService.listLearners(auth, keyword, deptKeyword, deptId, page, limit);
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }
}
