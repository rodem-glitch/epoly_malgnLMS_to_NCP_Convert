package kr.go.growailms.tutor.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 왜: 독립 도메인으로 분리하기엔 작은 단일 엔드포인트들을 모아둔 컨트롤러입니다.
 *     각 JSP 파일이 1~2개 정도의 기능만 담당하던 것들을 통합합니다.
 *
 * 엔드포인트 매핑:
 *   materials_list.jsp       → GET    /api/tutor/materials?course_id=
 *   materials_upload.jsp     → POST   /api/tutor/materials
 *   materials_delete.jsp     → POST   /api/tutor/materials/{id}/delete
 *   certificate_templates    → GET    /api/tutor/certificates/templates
 *   certificate_issue        → POST   /api/tutor/certificates/issue
 *   completion_list.jsp      → GET    /api/tutor/completions?course_id=
 *   completion_update.jsp    → POST   /api/tutor/completions
 *   grades_list.jsp          → GET    /api/tutor/grades?course_id=
 *   grades_recalc.jsp        → POST   /api/tutor/grades/recalc
 *   learner_list.jsp         → GET    /api/tutor/learners
 *   tutor_list.jsp           → GET    /api/tutor/tutors
 *   privacy_log.jsp          → GET    /api/tutor/privacy-log
 *   wishlist_toggle.jsp      → POST   /api/tutor/wishlist/toggle
 *   statistics_proxy.jsp     → GET    /api/tutor/statistics-proxy
 *   content_recommend.jsp    → GET    /api/tutor/content-recommend
 *   external_link_lesson_upsert → POST /api/tutor/external-lessons
 *   kollus_callback          → POST   /api/tutor/kollus/callback
 *   kollus_upload_url        → GET    /api/tutor/kollus/upload-url
 *   kollus_list              → GET    /api/tutor/kollus/list
 */
@RestController
@RequestMapping("/api/tutor")
public class TutorMiscController {

    private static final Logger log = LoggerFactory.getLogger(TutorMiscController.class);

    private final TutorMiscService miscService;

    public TutorMiscController(TutorMiscService miscService) {
        this.miscService = miscService;
    }

    // ==================== 학습자료 (Materials) ====================

    // ========== materials_list.jsp ==========
    @GetMapping("/materials")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listMaterials(
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "lesson_id", defaultValue = "0") int lessonId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("학습자료 목록: userId={}, courseId={}", auth.userId(), courseId);

        List<Map<String, Object>> list = miscService.listMaterials(auth, courseId, lessonId);
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }

    // ========== materials_upload.jsp ==========
    @PostMapping("/materials")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> uploadMaterial(
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "lesson_id", defaultValue = "0") int lessonId,
            @RequestParam(name = "title", defaultValue = "") String title,
            @RequestParam(name = "file") MultipartFile file
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("학습자료 업로드: userId={}, courseId={}, fileName={}", auth.userId(), courseId, file.getOriginalFilename());

        Map<String, Object> result = miscService.uploadMaterial(auth, courseId, lessonId, title, file);
        return ResponseEntity.ok(TutorApiResponse.success(result));
    }

    // ========== materials_delete.jsp ==========
    @PostMapping("/materials/{id}/delete")
    public ResponseEntity<TutorApiResponse<Integer>> deleteMaterial(
            @PathVariable("id") int id,
            @RequestParam(name = "course_id") int courseId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("학습자료 삭제: userId={}, materialId={}", auth.userId(), id);

        boolean ok = miscService.deleteMaterial(auth, courseId, id);
        if (!ok) {
            return ResponseEntity.ok(TutorApiResponse.error(
                    TutorApiResponse.CODE_NO_EDIT_PERMISSION, "자료 삭제 권한이 없습니다."));
        }
        return ResponseEntity.ok(TutorApiResponse.success(id));
    }

    // ==================== 수료증 (Certificates) ====================

    // ========== certificate_templates.jsp ==========
    @GetMapping("/certificates/templates")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listCertificateTemplates() {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("수료증 템플릿 목록: userId={}", auth.userId());

        List<Map<String, Object>> list = miscService.listCertificateTemplates(auth);
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }

    // ========== certificate_issue.jsp ==========
    @PostMapping("/certificates/issue")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> issueCertificate(
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "user_id") int userId,
            @RequestParam(name = "template_id", defaultValue = "0") int templateId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("수료증 발급: authUserId={}, courseId={}, targetUserId={}", auth.userId(), courseId, userId);

        Map<String, Object> result = miscService.issueCertificate(auth, courseId, userId, templateId);
        return ResponseEntity.ok(TutorApiResponse.success(result));
    }

    // ==================== 수료 관리 (Completions) ====================

    // ========== completion_list.jsp ==========
    @GetMapping("/completions")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listCompletions(
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "s_keyword", defaultValue = "") String keyword,
            @RequestParam(name = "complete_yn", defaultValue = "") String completeYn
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("수료 관리 목록: userId={}, courseId={}", auth.userId(), courseId);

        List<Map<String, Object>> list = miscService.listCompletions(auth, courseId, keyword, completeYn);
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }

    // ========== completion_update.jsp ==========
    @PostMapping("/completions")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> updateCompletion(
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "user_id") int userId,
            @RequestParam(name = "complete_yn") String completeYn,
            @RequestParam(name = "complete_status", defaultValue = "") String completeStatus
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("수료 상태 변경: userId={}, courseId={}, targetUserId={}, completeYn={}",
                auth.userId(), courseId, userId, completeYn);

        boolean ok = miscService.updateCompletion(auth, courseId, userId, completeYn, completeStatus);
        if (!ok) {
            return ResponseEntity.ok(TutorApiResponse.error(
                    TutorApiResponse.CODE_NO_EDIT_PERMISSION, "수료 상태 변경 권한이 없습니다."));
        }
        return ResponseEntity.ok(TutorApiResponse.success(Map.of("course_id", courseId, "user_id", userId)));
    }

    // ==================== 성적 관리 (Grades) ====================

    // ========== grades_list.jsp ==========
    @GetMapping("/grades")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listGrades(
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "s_keyword", defaultValue = "") String keyword
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("성적 목록: userId={}, courseId={}", auth.userId(), courseId);

        List<Map<String, Object>> list = miscService.listGrades(auth, courseId, keyword);
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }

    // ========== grades_recalc.jsp ==========
    @PostMapping("/grades/recalc")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> recalcGrades(
            @RequestParam(name = "course_id") int courseId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("성적 재계산: userId={}, courseId={}", auth.userId(), courseId);

        Map<String, Object> result = miscService.recalcGrades(auth, courseId);
        return ResponseEntity.ok(TutorApiResponse.success(result));
    }

    // ==================== 학습자/교수자 조회 ====================

    // ========== learner_list.jsp ==========
    @GetMapping("/learners")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listLearners(
            @RequestParam(name = "s_keyword", defaultValue = "") String keyword,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("학습자 목록: userId={}", auth.userId());

        List<Map<String, Object>> list = miscService.listLearners(auth, keyword, page, pageSize);
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }

    // ========== tutor_list.jsp ==========
    @GetMapping("/tutors")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listTutors(
            @RequestParam(name = "s_keyword", defaultValue = "") String keyword
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("교수자 목록: userId={}", auth.userId());

        List<Map<String, Object>> list = miscService.listTutors(auth, keyword);
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }

    // ==================== 개인정보 로그 ====================

    // ========== privacy_log.jsp ==========
    @GetMapping("/privacy-log")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listPrivacyLog(
            @RequestParam(name = "target_user_id", defaultValue = "0") int targetUserId,
            @RequestParam(name = "start_date", defaultValue = "") String startDate,
            @RequestParam(name = "end_date", defaultValue = "") String endDate,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("개인정보 열람 로그: userId={}", auth.userId());

        List<Map<String, Object>> list = miscService.listPrivacyLog(auth, targetUserId,
                startDate, endDate, page, pageSize);
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }

    // ==================== 위시리스트 ====================

    // ========== wishlist_toggle.jsp ==========
    @PostMapping("/wishlist/toggle")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> toggleWishlist(
            @RequestParam(name = "course_id") int courseId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("위시리스트 토글: userId={}, courseId={}", auth.userId(), courseId);

        Map<String, Object> result = miscService.toggleWishlist(auth, courseId);
        return ResponseEntity.ok(TutorApiResponse.success(result));
    }

    // ==================== 통계 프록시 ====================

    // ========== statistics_proxy.jsp ==========
    @GetMapping("/statistics-proxy")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> statisticsProxy(
            @RequestParam(name = "course_id", defaultValue = "0") int courseId,
            @RequestParam(name = "type", defaultValue = "summary") String type
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("통계 프록시: userId={}, type={}", auth.userId(), type);

        Map<String, Object> result = miscService.getStatisticsProxy(auth, courseId, type);
        return ResponseEntity.ok(TutorApiResponse.success(result));
    }

    // ==================== 콘텐츠 추천 ====================

    // ========== content_recommend.jsp ==========
    @GetMapping("/content-recommend")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> contentRecommend(
            @RequestParam(name = "course_id", defaultValue = "0") int courseId,
            @RequestParam(name = "category_id", defaultValue = "0") int categoryId,
            @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("콘텐츠 추천: userId={}, courseId={}", auth.userId(), courseId);

        List<Map<String, Object>> list = miscService.getContentRecommend(auth, courseId, categoryId, limit);
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }

    // ==================== 외부 링크 레슨 ====================

    // ========== external_link_lesson_upsert.jsp ==========
    @PostMapping("/external-lessons")
    public ResponseEntity<TutorApiResponse<Long>> upsertExternalLesson(
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "lesson_id", defaultValue = "0") int lessonId,
            @RequestParam(name = "lesson_nm") String lessonNm,
            @RequestParam(name = "content_url") String contentUrl,
            @RequestParam(name = "run_time", defaultValue = "0") int runTime,
            @RequestParam(name = "section_id", defaultValue = "0") int sectionId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("외부 링크 레슨 등록/수정: userId={}, courseId={}, lessonNm={}", auth.userId(), courseId, lessonNm);

        long resultId = miscService.upsertExternalLesson(auth, courseId, lessonId,
                lessonNm, contentUrl, runTime, sectionId);
        if (resultId <= 0) {
            return ResponseEntity.ok(TutorApiResponse.error(
                    TutorApiResponse.CODE_NO_EDIT_PERMISSION, "외부 링크 레슨 등록 권한이 없습니다."));
        }
        return ResponseEntity.ok(TutorApiResponse.success(resultId));
    }

    // ==================== Kollus 동영상 ====================

    // ========== kollus_upload_url.jsp ==========
    @GetMapping("/kollus/upload-url")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> kollusUploadUrl(
            @RequestParam(name = "course_id", defaultValue = "0") int courseId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("Kollus 업로드 URL 요청: userId={}", auth.userId());

        Map<String, Object> result = miscService.getKollusUploadUrl(auth, courseId);
        return ResponseEntity.ok(TutorApiResponse.success(result));
    }

    // ========== kollus_callback.jsp ==========
    @PostMapping("/kollus/callback")
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> kollusCallback(
            @RequestParam Map<String, String> params
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("Kollus 콜백: userId={}", auth.userId());

        Map<String, Object> result = miscService.handleKollusCallback(auth, params);
        return ResponseEntity.ok(TutorApiResponse.success(result));
    }

    // ========== kollus_list.jsp ==========
    @GetMapping("/kollus/list")
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> kollusList(
            @RequestParam(name = "s_keyword", defaultValue = "") String keyword,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("Kollus 동영상 목록: userId={}", auth.userId());

        List<Map<String, Object>> list = miscService.listKollusVideos(auth, keyword, page, pageSize);
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }
}
