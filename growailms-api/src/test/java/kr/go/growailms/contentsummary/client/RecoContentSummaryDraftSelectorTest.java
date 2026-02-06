package kr.go.growailms.contentsummary.client;

import java.util.List;
import kr.go.growailms.contentsummary.dto.RecoContentSummaryDraft;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RecoContentSummaryDraftSelectorTest {

    @Test
    void fixed가_너무짧으면_raw를_우선한다() {
        int target = 260;

        RecoContentSummaryDraft raw = new RecoContentSummaryDraft(
            "A, B",
            "가".repeat(200),
            List.of("k1", "k2")
        );
        RecoContentSummaryDraft fixed = new RecoContentSummaryDraft(
            "A, B",
            "가".repeat(20),
            List.of()
        );

        RecoContentSummaryDraft picked = RecoContentSummaryDraftSelector.pickBetterDraft(raw, fixed, target);
        Assertions.assertEquals(raw, picked);
    }

    @Test
    void 둘다_충분히길면_목표길이에_가까운쪽을_우선한다() {
        int target = 260;

        RecoContentSummaryDraft raw = new RecoContentSummaryDraft(
            "A, B",
            "가".repeat(500),
            List.of("k1")
        );
        RecoContentSummaryDraft fixed = new RecoContentSummaryDraft(
            "A, B",
            "가".repeat(260),
            List.of("k1")
        );

        RecoContentSummaryDraft picked = RecoContentSummaryDraftSelector.pickBetterDraft(raw, fixed, target);
        Assertions.assertEquals(fixed, picked);
    }
}

