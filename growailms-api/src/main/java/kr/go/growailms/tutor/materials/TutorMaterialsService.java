package kr.go.growailms.tutor.materials;

import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 왜: materials_list.jsp, materials_upload.jsp, materials_delete.jsp의 비즈니스 로직을 모아둔 서비스입니다.
 *     과목에 연결된 자료(LM_LIBRARY + LM_COURSE_LIBRARY) 관리를 담당합니다.
 */
@Service
public class TutorMaterialsService {

    private static final Logger log = LoggerFactory.getLogger(TutorMaterialsService.class);

    private final TutorMaterialsJdbcRepository repo;

    public TutorMaterialsService(TutorMaterialsJdbcRepository repo) {
        this.repo = repo;
    }

    // ========== materials_list.jsp ==========

    public List<Map<String, Object>> listMaterials(TutorAuthContext.AuthInfo auth, int courseId) {
        // 왜: 관리자는 전체 과목, 교수자는 주담당 과목만 조회 가능
        if (!auth.isAdmin() && !repo.isMajorTutor(auth.userId(), courseId, auth.siteId())) {
            log.warn("자료 목록 권한 없음: userId={}, courseId={}", auth.userId(), courseId);
            return Collections.emptyList();
        }
        return repo.listMaterials(courseId, auth.siteId());
    }

    // ========== materials_upload.jsp ==========

    public Optional<Map<String, Object>> uploadMaterial(TutorAuthContext.AuthInfo auth, int courseId,
                                                         String libraryNm, String content, String libraryLink,
                                                         MultipartFile libraryFile) {
        if (!auth.isAdmin() && !repo.isMajorTutor(auth.userId(), courseId, auth.siteId())) {
            return Optional.empty();
        }

        // TODO: 파일 업로드 처리 — MultipartFile → 서버 저장 경로
        String savedFilePath = "";
        if (libraryFile != null && !libraryFile.isEmpty()) {
            // TODO: 파일 저장 로직 구현
            savedFilePath = libraryFile.getOriginalFilename();
        }

        long libraryId = repo.insertLibrary(auth.siteId(), libraryNm, content, libraryLink, savedFilePath);
        repo.linkCourseLibrary(courseId, libraryId, auth.siteId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("library_id", libraryId);
        result.put("course_id", courseId);
        return Optional.of(result);
    }

    // ========== materials_delete.jsp ==========

    public boolean deleteMaterial(TutorAuthContext.AuthInfo auth, int courseId, int libraryId) {
        if (!auth.isAdmin() && !repo.isMajorTutor(auth.userId(), courseId, auth.siteId())) {
            return false;
        }
        // 왜: 과목-자료 연결만 해제 (자료 자체는 삭제하지 않음 — 다른 과목에서 사용 중일 수 있음)
        return repo.unlinkCourseLibrary(courseId, libraryId, auth.siteId()) > 0;
    }
}
