package kr.go.growailms.contentsummary.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import kr.go.growailms.contentsummary.dto.EnqueueBackfillResponse;
import kr.go.growailms.contentsummary.dto.KollusWebhookIngestResponse;
import kr.go.growailms.contentsummary.dto.RunTranscriptionResponse;
import kr.go.growailms.contentsummary.entity.KollusTranscript;
import kr.go.growailms.contentsummary.repository.ContentSummaryRepository;
import kr.go.growailms.contentsummary.service.ContentSummaryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/contentsummary")
public class ContentSummaryController {

    private final ContentSummaryService contentSummaryService;
    private final ContentSummaryRepository transcriptRepository;
    private final HttpServletRequest httpServletRequest;
    private final ObjectMapper objectMapper;

    public ContentSummaryController(
        ContentSummaryService contentSummaryService,
        ContentSummaryRepository transcriptRepository,
        HttpServletRequest httpServletRequest,
        ObjectMapper objectMapper
    ) {
        // 왜: 전사 실행은 운영 리소스를 많이 쓰는 작업이라, API로 제어 가능하게 하되 기본은 로컬에서만 실행되게 막습니다.
        this.contentSummaryService = contentSummaryService;
        this.transcriptRepository = transcriptRepository;
        this.httpServletRequest = httpServletRequest;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/admin/transcribe")
    public RunTranscriptionResponse runTranscription(
        @RequestHeader(name = "X-ContentSummary-Admin-Token", required = false) String adminToken,
        @RequestParam(name = "siteId", required = false) Integer siteId,
        @RequestParam(name = "limit", defaultValue = "20") int limit,
        @RequestParam(name = "force", defaultValue = "false") boolean force,
        @RequestParam(name = "keyword", required = false) String keyword
    ) {
        ensureAdminAccess(adminToken);
        return contentSummaryService.runTranscription(siteId, limit, force, keyword);
    }

    @PostMapping("/admin/enqueue/backfill")
    public EnqueueBackfillResponse enqueueBackfill(
        @RequestHeader(name = "X-ContentSummary-Admin-Token", required = false) String adminToken,
        @RequestParam(name = "siteId", required = false) Integer siteId,
        @RequestParam(name = "limit", defaultValue = "3000") int limit,
        @RequestParam(name = "keyword", required = false) String keyword
    ) {
        ensureAdminAccess(adminToken);
        return contentSummaryService.enqueueBackfill(siteId, limit, keyword);
    }

    /**
     * 기존 영상들의 duration_seconds를 백필합니다.
     * DB에 이미 있는 영상 중 duration_seconds가 null인 레코드에 대해 Kollus API로 길이를 조회하여 저장합니다.
     */
    @PostMapping("/admin/backfill-durations")
    public java.util.Map<String, Object> backfillDurations(
        @RequestHeader(name = "X-ContentSummary-Admin-Token", required = false) String adminToken
    ) {
        ensureAdminAccess(adminToken);
        int count = contentSummaryService.backfillDurations();
        return java.util.Map.of("status", "OK", "updatedCount", count);
    }

    @PostMapping("/webhooks/kollus")
    public KollusWebhookIngestResponse ingestKollusWebhook(
        @RequestHeader(name = "X-Kollus-Webhook-Token", required = false) String webhookToken,
        @RequestParam(name = "token", required = false) String webhookTokenParam,
        @RequestParam(name = "siteId", required = false) Integer siteId,
        @RequestParam(name = "media_content_key", required = false) String mediaContentKeyParam,
        @RequestParam(name = "upload_file_key", required = false) String uploadFileKeyParam,
        @RequestParam(name = "title", required = false) String titleParam,
        @org.springframework.web.bind.annotation.RequestBody(required = false) String body
    ) {
        ensureKollusWebhookAccess(webhookToken != null ? webhookToken : webhookTokenParam);

        // 왜: Kollus Webhook은 연동 방식에 따라 JSON 또는 form-urlencoded로 올 수 있습니다.
        // - form-urlencoded면 RequestParam으로 값이 들어오므로 그걸 우선 사용합니다.
        // - JSON이면 body에서 찾아옵니다.
        JsonNode bodyNode = null;
        if (body != null && !body.isBlank()) {
            try {
                bodyNode = objectMapper.readTree(body);
            } catch (Exception ignored) {
                // form-urlencoded일 수 있으니 JSON 파싱 실패는 무시합니다.
            }
        }

        String mediaContentKey = firstNonBlank(mediaContentKeyParam, uploadFileKeyParam);
        if ((mediaContentKey == null || mediaContentKey.isBlank()) && bodyNode != null && !bodyNode.isNull()) {
            mediaContentKey = findAnyText(bodyNode,
                "/media_content_key",
                "/mediaContentKey",
                "/upload_file_key",
                "/uploadFileKey",
                "/data/media_content_key",
                "/data/upload_file_key",
                "/result/media_content_key",
                "/result/item/media_content_key",
                "/result/item/upload_file_key"
            );
        }

        if (mediaContentKey == null || mediaContentKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "media_content_key(또는 upload_file_key)를 찾을 수 없습니다.");
        }

        String title = titleParam;
        if ((title == null || title.isBlank()) && bodyNode != null && !bodyNode.isNull()) {
            title = findAnyText(bodyNode,
                "/title",
                "/data/title",
                "/result/title",
                "/result/item/title"
            );
        }

        return contentSummaryService.ingestKollusWebhook(siteId, mediaContentKey, title);
    }

    @GetMapping("/transcriptions/{mediaContentKey}")
    public KollusTranscript getTranscript(
        @RequestParam(name = "siteId", required = false) Integer siteId,
        @PathVariable("mediaContentKey") String mediaContentKey
    ) {
        Integer safeSiteId = siteId == null ? 1 : siteId;
        return transcriptRepository.findBySiteIdAndMediaContentKey(safeSiteId, mediaContentKey)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "전사 결과를 찾을 수 없습니다."));
    }

    private void ensureAdminAccess(String adminToken) {
        // 왜: 전사 실행은 비용/시간이 큰 작업이라 외부에서 무작정 호출되면 바로 장애가 납니다.
        String expected = System.getenv("CONTENTSUMMARY_ADMIN_TOKEN");

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
                "로컬에서만 실행 가능합니다. 운영에서 사용하려면 CONTENTSUMMARY_ADMIN_TOKEN을 설정해 주세요."
            );
        }
    }

    private void ensureKollusWebhookAccess(String webhookToken) {
        // 왜: webhook 엔드포인트는 외부에서 접근할 수밖에 없어서, 최소한 "공유 토큰"으로 막아야 안전합니다.
        String expected = System.getenv("KOLLUS_WEBHOOK_TOKEN");

        if (expected != null && !expected.isBlank()) {
            if (webhookToken == null || !expected.equals(webhookToken)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Webhook 토큰이 올바르지 않습니다.");
            }
            return;
        }

        // 토큰이 없으면 개발/로컬 테스트만 허용
        String remoteAddr = httpServletRequest.getRemoteAddr();
        boolean isLocal = "127.0.0.1".equals(remoteAddr) || "::1".equals(remoteAddr);
        if (!isLocal) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "로컬에서만 실행 가능합니다. 운영에서 사용하려면 KOLLUS_WEBHOOK_TOKEN을 설정해 주세요."
            );
        }
    }

    private static String findAnyText(com.fasterxml.jackson.databind.JsonNode root, String... jsonPointers) {
        if (root == null) return null;
        for (String p : jsonPointers) {
            if (p == null || p.isBlank()) continue;
            com.fasterxml.jackson.databind.JsonNode node = root.at(p);
            if (node == null || node.isMissingNode() || node.isNull()) continue;
            String s = node.asText(null);
            if (s != null && !s.isBlank()) return s.trim();
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v == null) continue;
            String t = v.trim();
            if (!t.isBlank()) return t;
        }
        return null;
    }
}
