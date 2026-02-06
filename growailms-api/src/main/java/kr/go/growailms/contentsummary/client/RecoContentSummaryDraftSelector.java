package kr.go.growailms.contentsummary.client;

import java.util.List;
import kr.go.growailms.contentsummary.dto.RecoContentSummaryDraft;

final class RecoContentSummaryDraftSelector {

    private RecoContentSummaryDraftSelector() {
    }

    static RecoContentSummaryDraft pickBetterDraft(
        RecoContentSummaryDraft fromRaw,
        RecoContentSummaryDraft fromFixed,
        int targetSummaryLength
    ) {
        // 왜: Gemini가 JSON을 깨뜨리면(raw/fixed) strict 파싱이 실패해서 "느슨한 파싱" 결과를 저장하게 됩니다.
        // 그런데 재정렬(fixed) 출력은 종종 중간에서 잘려 summary가 몇 글자만 남는 경우가 있어,
        // 무조건 fixed를 우선하면 DB에 요약이 짧게 저장되는 문제가 발생합니다.
        //
        // 해결: raw/fixed를 모두 비교해서
        // - 한쪽만 충분히 길면 그쪽을 선택
        // - 둘 다 충분히 길면 목표 길이에 더 가까운 쪽을 선택
        // - 둘 다 짧으면 더 긴 쪽을 선택(최소한의 정보라도 남기기 위해)
        RecoContentSummaryDraft safeRaw = fromRaw == null
            ? new RecoContentSummaryDraft("기타, 기타", "", List.of())
            : fromRaw;
        RecoContentSummaryDraft safeFixed = fromFixed == null
            ? new RecoContentSummaryDraft("기타, 기타", "", List.of())
            : fromFixed;

        int safeTarget = Math.max(1, targetSummaryLength);
        int minAcceptable = Math.max(80, (int) Math.round(safeTarget * 0.5d));

        int rawLen = codePointLength(safeRaw.summary());
        int fixedLen = codePointLength(safeFixed.summary());

        boolean rawOk = rawLen >= minAcceptable;
        boolean fixedOk = fixedLen >= minAcceptable;

        if (rawOk && !fixedOk) return safeRaw;
        if (fixedOk && !rawOk) return safeFixed;

        if (rawOk && fixedOk) {
            int rawDiff = Math.abs(rawLen - safeTarget);
            int fixedDiff = Math.abs(fixedLen - safeTarget);
            if (rawDiff != fixedDiff) return rawDiff < fixedDiff ? safeRaw : safeFixed;

            int rawKeywordCount = safeRaw.keywords() == null ? 0 : safeRaw.keywords().size();
            int fixedKeywordCount = safeFixed.keywords() == null ? 0 : safeFixed.keywords().size();
            if (rawKeywordCount != fixedKeywordCount) return rawKeywordCount > fixedKeywordCount ? safeRaw : safeFixed;
            return rawLen >= fixedLen ? safeRaw : safeFixed;
        }

        if (rawLen != fixedLen) return rawLen > fixedLen ? safeRaw : safeFixed;
        return safeFixed;
    }

    private static int codePointLength(String s) {
        if (s == null) return 0;
        String t = s.trim();
        if (t.isBlank()) return 0;
        return t.codePointCount(0, t.length());
    }
}

