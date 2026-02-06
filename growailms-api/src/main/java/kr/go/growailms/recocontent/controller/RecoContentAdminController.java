package kr.go.growailms.recocontent.controller;

import jakarta.servlet.http.HttpServletRequest;
import kr.go.growailms.recocontent.service.RecoContentImportService;
import kr.go.growailms.recocontent.service.RecoContentVectorIndexService;
import kr.go.growailms.recocontent.service.dto.ImportRecoContentsSampleRequest;
import kr.go.growailms.recocontent.service.dto.ImportRecoContentsResponse;
import kr.go.growailms.recocontent.service.dto.IndexRecoContentsRequest;
import kr.go.growailms.recocontent.service.dto.IndexRecoContentsResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/reco-contents/admin")
public class RecoContentAdminController {

    private final RecoContentImportService recoContentImportService;
    private final RecoContentVectorIndexService recoContentVectorIndexService;
    private final HttpServletRequest httpServletRequest;

    public RecoContentAdminController(
        RecoContentImportService recoContentImportService,
        RecoContentVectorIndexService recoContentVectorIndexService,
        HttpServletRequest httpServletRequest
    ) {
        // 왜: 개발 단계에서 "샘플 적재 + 벡터 인덱싱"을 빠르게 반복할 수 있는 관리용 API를 분리합니다.
        this.recoContentImportService = recoContentImportService;
        this.recoContentVectorIndexService = recoContentVectorIndexService;
        this.httpServletRequest = httpServletRequest;
    }

    @PostMapping(
        value = "/import/sample-text",
        consumes = MediaType.TEXT_PLAIN_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ImportRecoContentsResponse importSampleText(
        @RequestHeader(name = "X-Reco-Admin-Token", required = false) String adminToken,
        @RequestParam(name = "replace", defaultValue = "false") boolean replace,
        @RequestBody(required = false) String sampleText
    ) {
        ensureAdminAccess(adminToken);
        return recoContentImportService.importFromSampleText(sampleText, replace);
    }

    @PostMapping(
        value = "/import/sample",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ImportRecoContentsResponse importSample(
        @RequestHeader(name = "X-Reco-Admin-Token", required = false) String adminToken,
        @RequestBody(required = false) ImportRecoContentsSampleRequest request
    ) {
        // 왜: 화면/배치에서 옵션까지 함께 보내려면 JSON 형식이 더 안전합니다.
        ensureAdminAccess(adminToken);
        return recoContentImportService.importFromSampleRequest(request);
    }

    @PostMapping("/index")
    public IndexRecoContentsResponse index(
        @RequestHeader(name = "X-Reco-Admin-Token", required = false) String adminToken,
        @RequestBody(required = false) IndexRecoContentsRequest request
    ) {
        // TODO: 테스트 후 주석 해제 필요!
        // ensureAdminAccess(adminToken);
        return recoContentVectorIndexService.indexFromDatabase(request);
    }

    private void ensureAdminAccess(String adminToken) {
        // 왜: 샘플 적재/재인덱싱은 운영 데이터에 영향을 줄 수 있어서, 기본은 로컬에서만 실행되게 막습니다.
        // - 운영에서 써야 하면 환경변수로 토큰을 설정하고, 헤더로 전달하도록 합니다.
        String expected = System.getenv("RECO_CONTENT_ADMIN_TOKEN");

        if (expected != null && !expected.isBlank()) {
            if (adminToken == null || !expected.equals(adminToken)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 토큰이 올바르지 않습니다.");
            }
            return;
        }

        String remoteAddr = httpServletRequest.getRemoteAddr();
        boolean isLocal = "127.0.0.1".equals(remoteAddr) || "::1".equals(remoteAddr);
        if (!isLocal) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "로컬에서만 실행할 수 있습니다. 운영에서는 RECO_CONTENT_ADMIN_TOKEN을 설정해 주세요."
            );
        }
    }
}
