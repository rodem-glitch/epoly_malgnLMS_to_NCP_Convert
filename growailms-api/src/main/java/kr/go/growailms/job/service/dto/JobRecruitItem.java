package kr.go.growailms.job.service.dto;

// 왜: 채용 공고 카드에 필요한 필드를 그대로 담는 DTO입니다.
public record JobRecruitItem(
    String wantedAuthNo,
    String company,
    String busino,
    String indTpNm,
    String title,
    String salTpNm,
    String sal,
    String minSal,
    String maxSal,
    String region,
    String holidayTpNm,
    String minEdubg,
    String career,
    String regDt,
    String closeDt,
    String infoSvc,
    String wantedInfoUrl,
    String wantedMobileInfoUrl,
    String smodifyDtm,
    String zipCd,
    String strtnmCd,
    String basicAddr,
    String detailAddr,
    String empTpCd,
    String jobsCd
) {
}
