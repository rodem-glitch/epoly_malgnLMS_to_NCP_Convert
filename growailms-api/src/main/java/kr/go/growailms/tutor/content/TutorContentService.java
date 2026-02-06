package kr.go.growailms.tutor.content;

import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 왜: content_recommend.jsp의 비즈니스 로직을 모아둔 서비스입니다.
 *     과목/차시 정보를 기반으로 콘텐츠 추천 목록을 반환합니다.
 *     레거시에서는 polytech-lms-api의 추천 엔드포인트를 프록시 호출했습니다.
 *     TODO: 실제 추천 API 호출 또는 자체 검색 로직 구현 필요
 */
@Service
public class TutorContentService {

    private static final Logger log = LoggerFactory.getLogger(TutorContentService.class);

    private final TutorContentJdbcRepository repo;

    public TutorContentService(TutorContentJdbcRepository repo) {
        this.repo = repo;
    }

    // ========== content_recommend.jsp ==========

    public List<Map<String, Object>> recommend(TutorAuthContext.AuthInfo auth,
                                                String courseName, String courseIntro, String courseDetail,
                                                String lessonTitle, String lessonDescription,
                                                String keywords, int topK, double similarityThreshold) {
        // 왜: 레거시에서는 polytech-lms-api(Spring Boot)의 추천 엔드포인트를 HTTP로 프록시 호출했음.
        //     이제 같은 Spring Boot 앱 안이므로, 직접 DB 검색 또는 내부 서비스 호출로 대체 가능.
        // TODO: 추천 엔진 연동 또는 키워드 기반 검색 로직 구현

        // 왜: 키워드가 있으면 DB에서 직접 검색하는 fallback 제공
        if (keywords != null && !keywords.isEmpty()) {
            return repo.searchByKeyword(auth.siteId(), keywords, topK);
        }

        // TODO: 추천 엔진 호출
        log.debug("콘텐츠 추천 요청 (미구현 stub): courseName={}, topK={}", courseName, topK);
        return Collections.emptyList();
    }
}
