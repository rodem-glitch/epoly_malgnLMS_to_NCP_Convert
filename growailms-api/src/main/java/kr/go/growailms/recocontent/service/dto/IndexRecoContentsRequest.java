package kr.go.growailms.recocontent.service.dto;

public record IndexRecoContentsRequest(
    Integer page,
    Integer size,
    Integer embeddingVersion
) {
    public int pageOrDefault() {
        return page == null || page < 0 ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null || size <= 0 ? 100 : Math.min(size, 500);
    }

    public int embeddingVersionOrDefault() {
        return embeddingVersion == null || embeddingVersion <= 0 ? 1 : embeddingVersion;
    }
}

