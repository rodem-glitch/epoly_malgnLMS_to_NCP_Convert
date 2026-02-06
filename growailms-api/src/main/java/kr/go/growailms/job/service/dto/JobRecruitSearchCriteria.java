package kr.go.growailms.job.service.dto;

// 왜: Work24 검색 파라미터를 서비스/클라이언트에서 공통으로 쓰기 위한 기준 DTO입니다.
public record JobRecruitSearchCriteria(
    String region,
    String occupation,
    String salTp,
    Integer minPay,
    Integer maxPay,
    String education,
    int startPage,
    int display,
    String callType
) {
}
