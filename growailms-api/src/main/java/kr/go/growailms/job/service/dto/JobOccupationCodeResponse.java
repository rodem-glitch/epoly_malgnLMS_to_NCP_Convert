package kr.go.growailms.job.service.dto;

// 왜: 직종 코드 조회 결과를 화면/API에서 그대로 쓰기 위한 응답 DTO입니다.
public record JobOccupationCodeResponse(
    int idx,
    String code,
    String title,
    String depth1,
    String depth2,
    String depth3
) {
}
