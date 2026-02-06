package kr.go.growailms.job.service.dto;

import java.util.List;

// 왜: Work24 검색 결과(메타 + 공고 리스트)를 화면에 한 번에 내려주기 위한 응답 DTO입니다.
public record JobRecruitListResponse(
    int total,
    int startPage,
    int display,
    List<JobRecruitItem> wanted
) {
}
