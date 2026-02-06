package kr.go.growailms.contentsummary;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import kr.go.growailms.PolytechLmsApiApplication;
import kr.go.growailms.contentsummary.client.KollusApiClient;
import kr.go.growailms.contentsummary.client.KollusProperties;
import kr.go.growailms.contentsummary.dto.KollusChannelContent;
import kr.go.growailms.contentsummary.entity.KollusTranscript;
import kr.go.growailms.contentsummary.repository.ContentSummaryRepository;
import kr.go.growailms.contentsummary.service.ContentSummaryService;
import kr.go.growailms.recocontent.entity.RecoContent;
import kr.go.growailms.recocontent.repository.RecoContentRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = PolytechLmsApiApplication.class)
@ActiveProfiles("local")
class ContentSummaryManualIntegrationTest {

    @Autowired
    KollusApiClient kollusApiClient;

    @Autowired
    KollusProperties kollusProperties;

    @Autowired
    ContentSummaryRepository transcriptRepository;

    @Autowired
    RecoContentRepository recoContentRepository;

    @Autowired
    ContentSummaryService contentSummaryService;

    @Test
    void tbRecoContent에없는영상키로_영상요약_1건_수동검증() {
        // 왜: 이 테스트는 Kollus/Gemini를 실제로 호출하고 DB(TB_RECO_CONTENT)에 저장까지 합니다.
        // 실수로 CI/일반 test에서 돌아가면 비용/부하가 발생할 수 있어서, 환경변수로만 실행되게 막습니다.
        Assumptions.assumeTrue(
            "true".equalsIgnoreCase(System.getenv("CONTENTSUMMARY_MANUAL_TEST")),
            "CONTENTSUMMARY_MANUAL_TEST=true 일 때만 실행됩니다."
        );

        Integer siteId = 1;

        // 왜: 가장 최근 콘텐츠 중에서 '아직 요약이 없는 것'을 골라야 테스트가 의미가 있습니다.
        KollusChannelContent candidate = pickOneCandidateNotInRecoContent();
        String mediaKey = safeTrim(candidate.mediaContentKey());
        String title = candidate.title();

        // 안전장치: 시작 시점에 TB_RECO_CONTENT에 없음을 다시 확인합니다.
        Assertions.assertFalse(
            recoContentRepository.existsByLessonId(mediaKey),
            "테스트 시작 전에 이미 TB_RECO_CONTENT(lesson_id)에 존재하는 키입니다: " + mediaKey
        );

        Long transcriptId = upsertTranscriptAsProcessing(siteId, mediaKey, title);

        long startNanos = System.nanoTime();
        contentSummaryService.processVideoSummaryById(transcriptId);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

        KollusTranscript transcript = transcriptRepository.findById(transcriptId)
            .orElseThrow(() -> new IllegalStateException("TB_KOLLUS_TRANSCRIPT에서 레코드를 찾지 못했습니다. id=" + transcriptId));

        Optional<RecoContent> recoContent = recoContentRepository.findTopByLessonIdOrderByIdDesc(mediaKey);

        // 결과는 Gradle/JUnit 출력으로 확인할 수 있도록 표준 출력으로 남깁니다.
        printResult(candidate, elapsedMs, transcript, recoContent.orElse(null));

        Assertions.assertEquals("DONE", transcript.getStatus(), "요약이 DONE으로 끝나지 않았습니다. last_error=" + transcript.getLastError());
        Assertions.assertTrue(recoContent.isPresent(), "TB_RECO_CONTENT에 저장된 결과가 없습니다.");
    }

    private KollusChannelContent pickOneCandidateNotInRecoContent() {
        int pagesToScan = envInt("CONTENTSUMMARY_MANUAL_SCAN_PAGES", 3);
        int perPage = envInt("CONTENTSUMMARY_MANUAL_PER_PAGE", 50);
        int preferMaxDurationSeconds = envInt("CONTENTSUMMARY_MANUAL_PREFER_MAX_DURATION_SECONDS", 900);

        List<KollusChannelContent> candidates = new ArrayList<>();

        for (int page = 1; page <= pagesToScan; page++) {
            List<KollusChannelContent> contents = kollusApiClient.listChannelContents(page, perPage, null);
            for (KollusChannelContent c : contents) {
                String mediaKey = safeTrim(c.mediaContentKey());
                if (mediaKey.isBlank()) continue;
                if (recoContentRepository.existsByLessonId(mediaKey)) continue;
                candidates.add(c);
            }
            // 왜: 후보를 너무 많이 모아도 실제로는 1건만 처리하므로, 적당히 모이면 멈춥니다.
            if (candidates.size() >= 30) break;
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                "TB_RECO_CONTENT에 없는 영상 키를 찾지 못했습니다. " +
                    "스캔 범위를 늘리려면 CONTENTSUMMARY_MANUAL_SCAN_PAGES 값을 키워주세요."
            );
        }

        // 왜: 영상이 너무 길면 다운로드/업로드/요약 시간이 크게 늘어나므로, 우선은 짧은 영상을 고릅니다.
        candidates.sort(Comparator.comparingInt(c -> safeSecondsOrMax(c.totalTimeSeconds())));

        for (KollusChannelContent c : candidates) {
            Integer seconds = c.totalTimeSeconds();
            if (seconds != null && seconds > 0 && seconds <= preferMaxDurationSeconds) {
                return c;
            }
        }

        return candidates.get(0);
    }

    private Long upsertTranscriptAsProcessing(Integer siteId, String mediaKey, String title) {
        String channelKey = safeTrim(kollusProperties.channelKey());
        final String resolvedChannelKey = channelKey.isBlank() ? "unknown" : channelKey;

        KollusTranscript transcript = transcriptRepository
            .findBySiteIdAndMediaContentKey(siteId, mediaKey)
            .orElseGet(() -> new KollusTranscript(siteId, resolvedChannelKey, mediaKey));

        transcript.markProcessing(title);
        KollusTranscript saved = transcriptRepository.save(transcript);
        if (saved.getId() == null) {
            throw new IllegalStateException("TB_KOLLUS_TRANSCRIPT PK(id)가 생성되지 않았습니다.");
        }
        return saved.getId();
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

    private static String formatHms(Integer seconds) {
        if (seconds == null || seconds <= 0) return "알수없음";
        int s = seconds;
        int h = s / 3600;
        int m = (s % 3600) / 60;
        int sec = s % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, sec);
        return String.format("%d:%02d", m, sec);
    }

    private static void printResult(
        KollusChannelContent candidate,
        long elapsedMs,
        KollusTranscript transcript,
        RecoContent recoContent
    ) {
        String mediaKey = safeTrim(candidate.mediaContentKey());
        Integer totalTimeSeconds = candidate.totalTimeSeconds();

        System.out.println("=== CONTENTSUMMARY_MANUAL_TEST_RESULT ===");
        System.out.println("mediaContentKey=" + mediaKey);
        System.out.println("title=" + oneLine(candidate.title()));
        System.out.println("videoLengthSeconds=" + (totalTimeSeconds == null ? "" : totalTimeSeconds));
        System.out.println("videoLengthHms=" + formatHms(totalTimeSeconds));
        System.out.println("elapsedMs=" + elapsedMs);
        System.out.println("elapsedSeconds=" + String.format("%.3f", elapsedMs / 1000.0d));
        System.out.println("transcriptId=" + (transcript == null ? "" : transcript.getId()));
        System.out.println("transcriptStatus=" + (transcript == null ? "" : transcript.getStatus()));
        System.out.println("lastError=" + (transcript == null ? "" : oneLine(transcript.getLastError())));

        if (recoContent == null) {
            System.out.println("recoContentId=");
            System.out.println("categoryNm=");
            System.out.println("summary=");
            System.out.println("keywords=");
        } else {
            System.out.println("recoContentId=" + recoContent.getId());
            System.out.println("categoryNm=" + oneLine(recoContent.getCategoryNm()));
            System.out.println("summary=" + oneLine(recoContent.getSummary()));
            System.out.println("keywords=" + oneLine(recoContent.getKeywords()));
        }

        System.out.println("=== END ===");
    }
}
