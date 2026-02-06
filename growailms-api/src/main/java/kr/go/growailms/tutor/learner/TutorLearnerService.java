package kr.go.growailms.tutor.learner;

import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 왜: learner_list.jsp의 비즈니스 로직을 모아둔 서비스입니다.
 *     과목개설 시 학습자 선택을 위한 회원 검색(이름/아이디/이메일, 부서 필터)을 담당합니다.
 *     테이블: TB_USER, TB_USER_DEPT
 */
@Service
public class TutorLearnerService {

    private static final Logger log = LoggerFactory.getLogger(TutorLearnerService.class);

    private final TutorLearnerJdbcRepository repo;

    public TutorLearnerService(TutorLearnerJdbcRepository repo) {
        this.repo = repo;
    }

    // ========== learner_list.jsp ==========

    public List<Map<String, Object>> listLearners(TutorAuthContext.AuthInfo auth, String keyword,
                                                    String deptKeyword, int deptId, int page, int limit) {
        int offset = (page - 1) * limit;
        return repo.listLearners(auth.siteId(), keyword, deptKeyword, deptId, offset, limit);
    }
}
