package kr.go.growailms.global.vector.service.dto;

public record IndexLessonsRequest(
    Integer siteId,
    String lessonType,
    Integer limit,
    Integer offset
) {
    public int limitOrDefault() {
        return limit == null ? 200 : Math.max(1, Math.min(limit, 2000));
    }

    public int offsetOrDefault() {
        return offset == null ? 0 : Math.max(0, offset);
    }
}

