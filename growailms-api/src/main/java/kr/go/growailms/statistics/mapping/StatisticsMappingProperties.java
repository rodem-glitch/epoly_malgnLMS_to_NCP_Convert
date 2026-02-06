package kr.go.growailms.statistics.mapping;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "statistics.mapping")
public class StatisticsMappingProperties {
    // 왜: 통계 화면에서 "캠퍼스 전공 비율"을 산업 분류로 비교하려면 전공-산업 매핑 파일이 필요합니다.
    // - 운영에서는 파일 경로를 환경변수로 주입하거나, DB 테이블로 적재해 관리하는 방식을 권장드립니다.

    private String majorIndustryFile;

    public String getMajorIndustryFile() {
        return majorIndustryFile;
    }

    public void setMajorIndustryFile(String majorIndustryFile) {
        this.majorIndustryFile = majorIndustryFile;
    }
}

