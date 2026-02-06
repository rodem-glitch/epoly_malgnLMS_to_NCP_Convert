package kr.go.growailms.contentsummary.dto;

public record EnqueueBackfillResponse(
    int scanned,
    int enqueued,
    int skippedDone
) {}

