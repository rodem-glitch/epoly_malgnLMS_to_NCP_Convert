package kr.go.growailms.tutor.materials;

import kr.go.growailms.tutor.common.TutorApiResponse;
import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 왜: tutor_lms/api/materials_*.jsp 3개를 하나의 REST 컨트롤러로 통합합니다.
 *     init.jsp 인증은 TutorAuthInterceptor가 처리하므로, 여기서는 비즈니스 로직만 담당합니다.
 *
 * 엔드포인트 매핑:
 *   materials_list.jsp    → GET    /api/tutor/materials?course_id=
 *   materials_upload.jsp  → POST   /api/tutor/materials
 *   materials_delete.jsp  → POST   /api/tutor/materials/delete
 */
@RestController
@RequestMapping("/api/tutor/materials")
public class TutorMaterialsController {

    private static final Logger log = LoggerFactory.getLogger(TutorMaterialsController.class);

    private final TutorMaterialsService materialsService;

    public TutorMaterialsController(TutorMaterialsService materialsService) {
        this.materialsService = materialsService;
    }

    // ========== materials_list.jsp ==========
    @GetMapping
    public ResponseEntity<TutorApiResponse<List<Map<String, Object>>>> listMaterials(
            @RequestParam(name = "course_id") int courseId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("자료 목록 조회: userId={}, courseId={}", auth.userId(), courseId);

        List<Map<String, Object>> list = materialsService.listMaterials(auth, courseId);
        return ResponseEntity.ok(TutorApiResponse.success(list, list.size()));
    }

    // ========== materials_upload.jsp ==========
    @PostMapping
    public ResponseEntity<TutorApiResponse<Map<String, Object>>> uploadMaterial(
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "library_nm", defaultValue = "") String libraryNm,
            @RequestParam(name = "content", defaultValue = "") String content,
            @RequestParam(name = "library_link", defaultValue = "") String libraryLink,
            @RequestParam(name = "library_file", required = false) MultipartFile libraryFile
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("자료 업로드: userId={}, courseId={}, libraryNm={}", auth.userId(), courseId, libraryNm);

        return materialsService.uploadMaterial(auth, courseId, libraryNm, content, libraryLink, libraryFile)
                .map(data -> ResponseEntity.ok(TutorApiResponse.success(data)))
                .orElse(ResponseEntity.ok(TutorApiResponse.error(
                        TutorApiResponse.CODE_NO_EDIT_PERMISSION, "자료 업로드 권한이 없습니다.")));
    }

    // ========== materials_delete.jsp ==========
    @PostMapping("/delete")
    public ResponseEntity<TutorApiResponse<Integer>> deleteMaterial(
            @RequestParam(name = "course_id") int courseId,
            @RequestParam(name = "library_id") int libraryId
    ) {
        TutorAuthContext.AuthInfo auth = TutorAuthContext.get();
        log.debug("자료 삭제: userId={}, courseId={}, libraryId={}", auth.userId(), courseId, libraryId);

        boolean ok = materialsService.deleteMaterial(auth, courseId, libraryId);
        if (!ok) {
            return ResponseEntity.ok(TutorApiResponse.error(
                    TutorApiResponse.CODE_NO_EDIT_PERMISSION, "자료 삭제 권한이 없습니다."));
        }
        return ResponseEntity.ok(TutorApiResponse.success(libraryId));
    }
}
