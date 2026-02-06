package kr.go.growailms.job.controller;

import kr.go.growailms.job.service.JobService;
import kr.go.growailms.job.service.JobService.CachePolicy;
import kr.go.growailms.job.service.dto.JobOccupationCodeResponse;
import kr.go.growailms.job.service.dto.JobCodeSyncResponse;
import kr.go.growailms.job.service.dto.JobRecruitListResponse;
import kr.go.growailms.job.service.dto.JobRegionCodeResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/job")
public class JobController {
    // 왜: 채용 기능은 화면에서 바로 쓰는 API이므로 `/job/*`으로 묶어 관리합니다.

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping("/region-codes")
    public List<JobRegionCodeResponse> regionCodes(
        @RequestParam(name = "depthType", required = false) String depthType,
        @RequestParam(name = "depthtype", required = false) String depthTypeLegacy,
        @RequestParam(name = "depth1", required = false) String depth1,
        @RequestParam(name = "provider", required = false) String provider
    ) {
        String resolvedDepthType = depthType != null ? depthType : depthTypeLegacy;
        JobService.Provider safeProvider = JobService.Provider.from(provider);
        return jobService.getRegionCodes(resolvedDepthType, depth1, safeProvider);
    }

    @GetMapping("/occupation-codes")
    public List<JobOccupationCodeResponse> occupationCodes(
        @RequestParam(name = "depthType", required = false) String depthType,
        @RequestParam(name = "depthtype", required = false) String depthTypeLegacy,
        @RequestParam(name = "depth1", required = false) String depth1,
        @RequestParam(name = "depth2", required = false) String depth2,
        @RequestParam(name = "provider", required = false) String provider
    ) {
        String resolvedDepthType = depthType != null ? depthType : depthTypeLegacy;
        JobService.Provider safeProvider = JobService.Provider.from(provider);
        return jobService.getOccupationCodes(resolvedDepthType, depth1, depth2, safeProvider);
    }

    @GetMapping("/recruits")
    public ResponseEntity<?> recruitList(
        @RequestParam(name = "region", required = false) String region,
        @RequestParam(name = "occupation", required = false) String occupation,
        @RequestParam(name = "salTp", required = false) String salTp,
        @RequestParam(name = "minPay", required = false) Integer minPay,
        @RequestParam(name = "maxPay", required = false) Integer maxPay,
        @RequestParam(name = "education", required = false) String education,
        @RequestParam(name = "startPage", required = false) Integer startPage,
        @RequestParam(name = "display", required = false) Integer display,
        @RequestParam(name = "provider", required = false) String provider,
        @RequestParam(name = "cachePolicy", required = false) String cachePolicy
    ) {
        try {
            CachePolicy policy = CachePolicy.from(cachePolicy);
            JobService.Provider safeProvider = JobService.Provider.from(provider);
            JobRecruitListResponse response = jobService.getRecruitments(
                region,
                occupation,
                salTp,
                minPay,
                maxPay,
                education,
                startPage,
                display,
                safeProvider,
                policy
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(e.getMessage()));
        } catch (DataAccessException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(mostSpecificMessage(e)));
        }
    }

    @GetMapping("/recruits/nl")
    public ResponseEntity<?> recruitListByNaturalLanguage(
        @RequestParam(name = "q", required = false) String query,
        @RequestParam(name = "region", required = false) String region,
        @RequestParam(name = "salTp", required = false) String salTp,
        @RequestParam(name = "minPay", required = false) Integer minPay,
        @RequestParam(name = "maxPay", required = false) Integer maxPay,
        @RequestParam(name = "education", required = false) String education,
        @RequestParam(name = "startPage", required = false) Integer startPage,
        @RequestParam(name = "display", required = false) Integer display,
        @RequestParam(name = "provider", required = false) String provider,
        @RequestParam(name = "cachePolicy", required = false) String cachePolicy
    ) {
        // 왜: 요청사항 - 자연어 검색은 통합(ALL)에서만 지원합니다.
        try {
            CachePolicy policy = CachePolicy.from(cachePolicy);
            JobService.Provider safeProvider = JobService.Provider.from(provider);
            if (safeProvider != JobService.Provider.ALL) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError("자연어 검색은 제공처=통합(ALL)에서만 지원합니다."));
            }

            JobRecruitListResponse response = jobService.getRecruitmentsByNaturalLanguageForAll(
                query,
                null, // 왜: 자연어 검색은 필터와 무관하게 통합(ALL)로만 조회합니다.
                null,
                null,
                null,
                null,
                startPage,
                display,
                policy
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(e.getMessage()));
        } catch (DataAccessException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(mostSpecificMessage(e)));
        }
    }

    @GetMapping("/recruits/related")
    public ResponseEntity<?> relatedRecruitList(
        @RequestParam(name = "occupation", required = false) String occupation,
        @RequestParam(name = "limit", required = false) Integer limit,
        @RequestParam(name = "cachePolicy", required = false) String cachePolicy
    ) {
        try {
            CachePolicy policy = CachePolicy.from(cachePolicy);
            JobRecruitListResponse response = jobService.getRelatedRecruitments(occupation, limit, policy);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(e.getMessage()));
        } catch (DataAccessException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(mostSpecificMessage(e)));
        }
    }

    @PostMapping("/recruits/refresh")
    public ResponseEntity<?> refreshRecruitList(
        @RequestHeader(name = "X-Job-Admin-Token", required = false) String adminToken,
        @RequestParam(name = "region", required = false) String region,
        @RequestParam(name = "occupation", required = false) String occupation,
        @RequestParam(name = "salTp", required = false) String salTp,
        @RequestParam(name = "minPay", required = false) Integer minPay,
        @RequestParam(name = "maxPay", required = false) Integer maxPay,
        @RequestParam(name = "education", required = false) String education,
        @RequestParam(name = "startPage", required = false) Integer startPage,
        @RequestParam(name = "display", required = false) Integer display
    ) {
        ensureAdminAccess(adminToken);
        try {
            JobRecruitListResponse response = jobService.refreshRecruitments(
                region,
                occupation,
                salTp,
                minPay,
                maxPay,
                education,
                startPage,
                display
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(e.getMessage()));
        } catch (DataAccessException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(mostSpecificMessage(e)));
        }
    }

    @PostMapping("/codes/refresh")
    public ResponseEntity<?> refreshCodeTables(
        @RequestHeader(name = "X-Job-Admin-Token", required = false) String adminToken,
        @RequestParam(name = "target", required = false) String target
    ) {
        ensureAdminAccess(adminToken);
        try {
            String safeTarget = (target == null || target.isBlank()) ? "ALL" : target.trim().toUpperCase();
            boolean refreshRegion = "ALL".equals(safeTarget) || "REGION".equals(safeTarget);
            boolean refreshOccupation = "ALL".equals(safeTarget) || "OCCUPATION".equals(safeTarget);
            JobCodeSyncResponse response = jobService.refreshWork24Codes(refreshRegion, refreshOccupation);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(e.getMessage()));
        } catch (DataAccessException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(mostSpecificMessage(e)));
        }
    }

    private void ensureAdminAccess(String adminToken) {
        // 왜: 캐시 갱신은 외부 호출이 많아질 수 있으므로 관리자 토큰으로만 열어둡니다.
        String expected = System.getenv("JOB_ADMIN_TOKEN");
        if (expected == null || expected.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "JOB_ADMIN_TOKEN이 설정되지 않아 외부 갱신을 허용하지 않습니다."
            );
        }
        if (adminToken == null || !expected.equals(adminToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 토큰이 올바르지 않습니다.");
        }
    }

    private record ApiError(String message) {
    }

    private static String mostSpecificMessage(DataAccessException e) {
        // 왜: SQL/테이블/권한 문제는 최종 원인 메시지가 있어야 빠르게 진단할 수 있습니다.
        Throwable cause = e.getMostSpecificCause();
        if (cause == null) return e.getMessage();
        String msg = cause.getMessage();
        return (msg == null || msg.isBlank()) ? e.getMessage() : msg;
    }
}
