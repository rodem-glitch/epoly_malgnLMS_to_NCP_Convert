package kr.go.growailms.tutor.kollus;

import kr.go.growailms.tutor.common.TutorApiResponse;
import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 왜: tutor_lms/api/kollus_*.jsp 7개를 하나의 REST 컨트롤러로 통합합니다.
 *     init.jsp 인증은 TutorAuthInterceptor가 처리하므로, 여기서는 비즈니스 로직만 담당합니다.
 *
 * 엔드포인트 매핑:
 *   kollus_list.jsp              → GET  /api/tutor/kollus
 *   kollus_upload_url.jsp        → POST /api/tutor/kollus/upload-url
 *   kollus_attach_channel.jsp    → POST /api/tutor/kollus/attach-channel
 *   kollus_lesson_upsert.jsp     → POST /api/tutor/kollus/lesson-upsert
 *   kollus_wishlist_list.jsp     → GET  /api/tutor/kollus/wishlist
 *   kollus_wishlist_toggle.jsp   → POST /api/tutor/kollus/wishlist/toggle
 *   kollus_wishlist_migrate.jsp  → POST /api/tutor/kollus/wishlist/migrate
 */
@RestController
@RequestMapping("/api/tutor/kollus")
public class TutorKollusController {

    private static final Logger log = LoggerFactory.getLogger(TutorKollusController.class);

    private final TutorKollusService kollusService;

    public TutorKollusController(TutorKollusService kollusService) {
        this.kollusService = kollusService;
    }

    // ========== kollus_list.jsp ==========
    @GetMapping
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> listKollus(
            @RequestParam(name = "s_channel", defaultValue = "") String channel,
            @RequestParam(name = "s_keyword", defaultValue = "") String keyword,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "version", defaultValue = "1") int version
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("콜러스 목록: userId={}, channel={}", auth.userId(), channel);

        Map<String, Object> result = kollusService.listKollus(auth, channel, keyword, page, pageSize, version);
        return ResponseEntity.ok(TutorApiResponse.success(result));
    }

    // ========== kollus_upload_url.jsp ==========
    @PostMapping("/upload-url")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> getUploadUrl(
            @RequestParam(name = "title", defaultValue = "") String title,
            @RequestParam(name = "category_key", defaultValue = "") String categoryKey,
            @RequestParam(name = "expire_time", defaultValue = "600") int expireTime,
            @RequestParam(name = "is_encryption_upload", defaultValue = "0") int isEncryptionUpload,
            @RequestParam(name = "is_audio_upload", defaultValue = "0") int isAudioUpload
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("콜러스 업로드 URL 요청: userId={}, title={}", auth.userId(), title);

        // 왜: Kollus API 토큰을 프론트엔드에 노출하지 않고, 서버에서 안전하게 관리
        return kollusService.getUploadUrl(auth, title, categoryKey, expireTime, isEncryptionUpload, isAudioUpload)
                .map(data -> ResponseEntity.ok(TutorApiResponse.success(data)))
                .orElse(ResponseEntity.ok(TutorApiResponse.error("5003", "Kollus 업로드 URL 생성에 실패했습니다.")));
    }

    // ========== kollus_attach_channel.jsp ==========
    @PostMapping("/attach-channel")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> attachChannel(
            @RequestParam(name = "upload_key") String uploadKey
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("콜러스 채널 연결: userId={}, uploadKey={}", auth.userId(), uploadKey);

        // 왜: 업로드 후 채널에 attach해야 목록에서 안정적으로 조회됨
        return kollusService.attachChannel(auth, uploadKey)
                .map(data -> ResponseEntity.ok(TutorApiResponse.success(data)))
                .orElse(ResponseEntity.ok(TutorApiResponse.error("5004", "채널 연결에 실패했습니다.")));
    }

    // ========== kollus_lesson_upsert.jsp ==========
    @PostMapping("/lesson-upsert")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> lessonUpsert(
            @RequestParam(name = "media_content_key") String mediaContentKey,
            @RequestParam(name = "title", defaultValue = "") String title,
            @RequestParam(name = "total_time", defaultValue = "0") int totalTime,
            @RequestParam(name = "content_width", defaultValue = "0") int contentWidth,
            @RequestParam(name = "content_height", defaultValue = "0") int contentHeight
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("콜러스 레슨 등록/수정: userId={}, mediaKey={}", auth.userId(), mediaContentKey);

        // 왜: media_content_key로 기존 레슨을 찾고, 없으면 새로 생성
        Map<String, Object> result = kollusService.upsertLesson(auth, mediaContentKey, title, totalTime,
                contentWidth, contentHeight);
        return ResponseEntity.ok(TutorApiResponse.success(result));
    }

    // ========== kollus_wishlist_list.jsp ==========
    @GetMapping("/wishlist")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listWishlist(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("콜러스 찜 목록: userId={}", auth.userId());

        List<Map<String, Object>> list = kollusService.listWishlist(auth, page, pageSize);
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }

    // ========== kollus_wishlist_toggle.jsp ==========
    @PostMapping("/wishlist/toggle")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> toggleWishlist(
            @RequestParam(name = "media_content_key") String mediaContentKey
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("콜러스 찜 토글: userId={}, mediaKey={}", auth.userId(), mediaContentKey);

        Map<String, Object> result = kollusService.toggleWishlist(auth, mediaContentKey);
        return ResponseEntity.ok(TutorApiResponse.success(result));
    }

    // ========== kollus_wishlist_migrate.jsp ==========
    @PostMapping("/wishlist/migrate")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> migrateWishlist() {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("콜러스 찜 마이그레이션: userId={}", auth.userId());

        Map<String, Object> result = kollusService.migrateWishlist(auth);
        return ResponseEntity.ok(TutorApiResponse.success(result));
    }
}
