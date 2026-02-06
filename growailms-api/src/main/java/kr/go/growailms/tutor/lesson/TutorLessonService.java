package kr.go.growailms.tutor.lesson;

import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 왜: external_link_lesson_upsert.jsp의 비즈니스 로직을 모아둔 서비스입니다.
 *     외부링크(URL)를 lesson_type='04'로 레슨에 등록/수정합니다.
 *     이미 동일 URL의 레슨이 있으면 그대로 사용, 없으면 새로 생성합니다.
 *     테이블: LM_LESSON
 */
@Service
public class TutorLessonService {

    private static final Logger log = LoggerFactory.getLogger(TutorLessonService.class);

    private final TutorLessonJdbcRepository repo;

    public TutorLessonService(TutorLessonJdbcRepository repo) {
        this.repo = repo;
    }

    // ========== external_link_lesson_upsert.jsp ==========

    public Map<String, Object> upsertExternalLink(TutorAuthContext.AuthInfo auth,
                                                    String url, String title, int totalTime) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 왜: 필수값 검증
        if (url == null || url.trim().isEmpty()) {
            result.put("code", "1001");
            result.put("error", "외부링크 URL이 필요합니다.");
            return result;
        }
        if (title == null || title.trim().isEmpty()) {
            result.put("code", "1002");
            result.put("error", "강의명이 필요합니다.");
            return result;
        }

        // 왜: 동일 URL의 레슨이 있으면 재사용
        Optional<Map<String, Object>> existing = repo.findLessonByUrl(url.trim(), auth.siteId());

        if (existing.isPresent()) {
            int lessonId = ((Number) existing.get().get("id")).intValue();
            // 왜: 제목이나 시간이 바뀌었을 수 있으므로 업데이트
            repo.updateExternalLesson(lessonId, title.trim(), totalTime);
            result.put("lesson_id", lessonId);
            result.put("action", "updated");
        } else {
            long newId = repo.insertExternalLesson(auth.siteId(), url.trim(), title.trim(), totalTime);
            result.put("lesson_id", newId);
            result.put("action", "inserted");
        }
        return result;
    }
}
