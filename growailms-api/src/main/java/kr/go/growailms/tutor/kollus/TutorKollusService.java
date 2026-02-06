package kr.go.growailms.tutor.kollus;

import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 왜: kollus_*.jsp 7개의 비즈니스 로직을 모아둔 서비스입니다.
 *     Kollus 외부 API 호출(업로드 URL, 채널 연결), 레슨 DB upsert, 찜 관리를 담당합니다.
 *     외부 API 호출 로직은 TODO로 표시 — 레거시 KollusDao 로직을 옮겨야 합니다.
 */
@Service
public class TutorKollusService {

    private static final Logger log = LoggerFactory.getLogger(TutorKollusService.class);

    private final TutorKollusJdbcRepository repo;

    public TutorKollusService(TutorKollusJdbcRepository repo) {
        this.repo = repo;
    }

    // ========== kollus_list.jsp ==========

    public Map<String, Object> listKollus(TutorAuthContext.AuthInfo auth, String channel,
                                           String keyword, int page, int pageSize, int version) {
        // TODO: Kollus 외부 API를 호출하여 채널 목록 및 미디어 목록을 가져오는 로직 필요
        //       레거시 KollusDao.getChannels(), KollusDao.getMediaList() 참조
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("channels", Collections.emptyList());
        result.put("media_list", Collections.emptyList());
        result.put("page", page);
        result.put("page_size", pageSize);
        return result;
    }

    // ========== kollus_upload_url.jsp ==========

    public Optional<Map<String, Object>> getUploadUrl(TutorAuthContext.AuthInfo auth, String title,
                                                       String categoryKey, int expireTime,
                                                       int isEncryptionUpload, int isAudioUpload) {
        // 왜: Kollus API 토큰을 서버에서 관리하여 프론트엔드 노출 방지
        // TODO: Kollus c-api-kr.kollus.com/api/upload/create-url 호출 구현
        //       레거시에서는 SiteDao에서 access_token을 읽어 사용
        String accessToken = repo.getKollusAccessToken(auth.siteId());
        if (accessToken == null || accessToken.isEmpty()) {
            return Optional.empty();
        }

        // TODO: 외부 HTTP 호출 구현
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("upload_url", "");
        result.put("progress_url", "");
        return Optional.of(result);
    }

    // ========== kollus_attach_channel.jsp ==========

    public Optional<Map<String, Object>> attachChannel(TutorAuthContext.AuthInfo auth, String uploadKey) {
        // TODO: Kollus API로 채널 연결 수행
        //       레거시 kollus_attach_channel.jsp 참조
        String channelKey = repo.getDefaultChannelKey(auth.siteId());
        if (channelKey == null || channelKey.isEmpty()) {
            return Optional.empty();
        }

        // TODO: 외부 HTTP 호출 구현
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("upload_key", uploadKey);
        result.put("channel_key", channelKey);
        result.put("attached", true);
        return Optional.of(result);
    }

    // ========== kollus_lesson_upsert.jsp ==========

    public Map<String, Object> upsertLesson(TutorAuthContext.AuthInfo auth, String mediaContentKey,
                                              String title, int totalTime, int contentWidth, int contentHeight) {
        // 왜: media_content_key로 기존 레슨을 찾고 없으면 새로 생성
        Optional<Map<String, Object>> existing = repo.findLessonByMediaKey(mediaContentKey, auth.siteId());

        Map<String, Object> result = new LinkedHashMap<>();
        if (existing.isPresent()) {
            int lessonId = ((Number) existing.get().get("id")).intValue();
            repo.updateLesson(lessonId, title, totalTime, contentWidth, contentHeight);
            result.put("lesson_id", lessonId);
            result.put("action", "updated");
        } else {
            long newId = repo.insertLesson(auth.siteId(), mediaContentKey, title, totalTime,
                    contentWidth, contentHeight);
            result.put("lesson_id", newId);
            result.put("action", "inserted");
        }
        return result;
    }

    // ========== kollus_wishlist_list.jsp ==========

    public List<Map<String, Object>> listWishlist(TutorAuthContext.AuthInfo auth, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return repo.listWishlist(auth.userId(), auth.siteId(), offset, pageSize);
    }

    // ========== kollus_wishlist_toggle.jsp ==========

    public Map<String, Object> toggleWishlist(TutorAuthContext.AuthInfo auth, String mediaContentKey) {
        boolean exists = repo.isWishlisted(auth.userId(), auth.siteId(), mediaContentKey);
        Map<String, Object> result = new LinkedHashMap<>();

        if (exists) {
            repo.removeWishlist(auth.userId(), auth.siteId(), mediaContentKey);
            result.put("wishlisted", false);
        } else {
            repo.addWishlist(auth.userId(), auth.siteId(), mediaContentKey);
            result.put("wishlisted", true);
        }
        result.put("media_content_key", mediaContentKey);
        return result;
    }

    // ========== kollus_wishlist_migrate.jsp ==========

    public Map<String, Object> migrateWishlist(TutorAuthContext.AuthInfo auth) {
        // TODO: 레거시 찜 데이터 마이그레이션 로직 구현
        int migrated = repo.migrateWishlist(auth.userId(), auth.siteId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("migrated_count", migrated);
        return result;
    }
}
