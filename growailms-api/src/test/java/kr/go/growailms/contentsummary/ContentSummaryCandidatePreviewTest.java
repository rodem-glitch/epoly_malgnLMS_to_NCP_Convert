package kr.go.growailms.contentsummary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kr.go.growailms.PolytechLmsApiApplication;
import kr.go.growailms.contentsummary.client.KollusApiClient;
import kr.go.growailms.contentsummary.dto.KollusChannelContent;
import kr.go.growailms.recocontent.repository.RecoContentRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = PolytechLmsApiApplication.class)
@ActiveProfiles("local")
class ContentSummaryCandidatePreviewTest {

    @Autowired
    KollusApiClient kollusApiClient;

    @Autowired
    RecoContentRepository recoContentRepository;

    @Test
    void tbRecoContent에없는영상_테스트예정목록_5건미리보기() {
        // 왜: 이 테스트는 Kollus/DB를 실제로 조회하므로 CI나 일반 테스트에서는 돌리면 안 됩니다.
        // CONTENTSUMMARY_MANUAL_TEST=true 일 때만 실행되도록 막아둡니다.
        Assumptions.assumeTrue(
            "true".equalsIgnoreCase(System.getenv("CONTENTSUMMARY_MANUAL_TEST")),
            "CONTENTSUMMARY_MANUAL_TEST=true 일 때만 실행됩니다."
        );

        int previewCount = Math.max(1, envInt("CONTENTSUMMARY_PREVIEW_COUNT", 5));
        int pagesToScan = Math.max(1, envInt("CONTENTSUMMARY_MANUAL_SCAN_PAGES", 3));
        int perPage = Math.max(1, envInt("CONTENTSUMMARY_MANUAL_PER_PAGE", 50));
        int preferMaxDurationSeconds = Math.max(1, envInt("CONTENTSUMMARY_MANUAL_PREFER_MAX_DURATION_SECONDS", 900));

        // 왜: 실제 테스트(ContentSummaryManualIntegrationTest)는 1회 실행마다 "DB에 없는 1개"를 자동 선택합니다.
        // 따라서 사전에 5개를 고르고 싶으면, "1개 선택 -> 처리됨으로 가정 -> 다음 1개 선택"을 5번 반복해서
        // 실제 실행 시 선택될 가능성이 높은 순서를 뽑아야 합니다.
        List<KollusChannelContent> planned = pickPlannedCandidates(previewCount, pagesToScan, perPage, preferMaxDurationSeconds);

        System.out.println("=== CONTENTSUMMARY_MANUAL_TEST_PLAN ===");
        System.out.println("plannedCount=" + planned.size());
        for (int i = 0; i < planned.size(); i++) {
            KollusChannelContent c = planned.get(i);
            String mediaKey = safeTrim(c.mediaContentKey());
            String title = oneLine(c.title());

            // 참고: 채널 목록 API의 totalTimeSeconds가 비어 있는 경우가 많아서,
            // 실제 길이는 media_token 기반 downloadInfo에서 다시 계산해 함께 보여드립니다.
            Integer resolvedSeconds = null;
            try {
                String mediaToken = kollusApiClient.issueMediaToken(mediaKey);
                KollusApiClient.DownloadInfo info = kollusApiClient.resolveDownloadInfoByMediaToken(mediaToken);
                resolvedSeconds = info.totalTimeSeconds();
            } catch (Exception ignored) {
            }

            System.out.println("index=" + (i + 1));
            System.out.println("mediaContentKey=" + mediaKey);
            System.out.println("title=" + title);
            System.out.println("durationSeconds=" + (resolvedSeconds == null ? "" : resolvedSeconds));
            System.out.println("---");
        }
        System.out.println("=== END PLAN ===");

        // 왜: 미리보기 단계에서 5개를 못 구하면, 다음 단계(5건 실제 테스트)도 진행이 불가능합니다.
        Assertions.assertTrue(
            planned.size() >= previewCount,
            "예정 목록을 " + previewCount + "건 확보하지 못했습니다. (확보=" + planned.size() + ")"
        );
    }

    private List<KollusChannelContent> pickPlannedCandidates(
        int previewCount,
        int pagesToScan,
        int perPage,
        int preferMaxDurationSeconds
    ) {
        List<KollusChannelContent> planned = new ArrayList<>();
        Set<String> alreadyPlannedKeys = new HashSet<>();

        for (int i = 0; i < previewCount; i++) {
            KollusChannelContent next = pickOneCandidateNotInRecoContent(pagesToScan, perPage, preferMaxDurationSeconds, alreadyPlannedKeys);
            planned.add(next);
            alreadyPlannedKeys.add(safeTrim(next.mediaContentKey()));
        }

        return planned;
    }

    private KollusChannelContent pickOneCandidateNotInRecoContent(
        int pagesToScan,
        int perPage,
        int preferMaxDurationSeconds,
        Set<String> excludeKeys
    ) {
        List<KollusChannelContent> candidates = new ArrayList<>();

        for (int page = 1; page <= pagesToScan; page++) {
            List<KollusChannelContent> contents = kollusApiClient.listChannelContents(page, perPage, null);
            for (KollusChannelContent c : contents) {
                String mediaKey = safeTrim(c.mediaContentKey());
                if (mediaKey.isBlank()) continue;
                if (excludeKeys != null && excludeKeys.contains(mediaKey)) continue;
                if (recoContentRepository.existsByLessonId(mediaKey)) continue;
                candidates.add(c);
            }
            // 왜: 실제 수동 테스트도 과도한 스캔을 피하려고 30건 정도면 멈추도록 되어 있습니다.
            if (candidates.size() >= 30) break;
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException("TB_RECO_CONTENT에 없는 영상 후보를 찾지 못했습니다. (스캔 페이지=" + pagesToScan + ")");
        }

        // 왜: 실제 수동 테스트와 동일하게, 채널 목록에서 길이를 얻을 수 있으면 짧은 영상을 우선합니다.
        candidates.sort(Comparator.comparingInt(c -> safeSecondsOrMax(c.totalTimeSeconds())));

        for (KollusChannelContent c : candidates) {
            Integer seconds = c.totalTimeSeconds();
            if (seconds != null && seconds > 0 && seconds <= preferMaxDurationSeconds) {
                return c;
            }
        }

        return candidates.get(0);
    }

    private static int envInt(String key, int defaultValue) {
        try {
            String raw = System.getenv(key);
            if (raw == null || raw.isBlank()) return defaultValue;
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static int safeSecondsOrMax(Integer seconds) {
        if (seconds == null) return Integer.MAX_VALUE;
        if (seconds <= 0) return Integer.MAX_VALUE;
        return seconds;
    }

    private static String safeTrim(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static String oneLine(String raw) {
        if (raw == null) return "";
        return raw.replace("\r", " ").replace("\n", " ").replaceAll("\\s+", " ").trim();
    }
}

