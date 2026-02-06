package kr.go.growailms.job.service.dto;

// 왜: Work24 코드 적재 결과를 화면/운영에서 바로 확인할 수 있게 합니다.
public record JobCodeSyncResponse(
    int regionCount,
    int occupationCount
) {
}
