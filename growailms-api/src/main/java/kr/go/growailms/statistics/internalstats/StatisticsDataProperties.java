package kr.go.growailms.statistics.internalstats;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "statistics.data")
public class StatisticsDataProperties {
    // 왜: 통계 원천 파일(입시/취업)은 운영에서 경로가 달라질 수 있어 설정으로 분리합니다.

    private String employmentFile;
    private String admissionFile;
    private String studentPopulationFile;

    public String getEmploymentFile() {
        return employmentFile;
    }

    public void setEmploymentFile(String employmentFile) {
        this.employmentFile = employmentFile;
    }

    public String getAdmissionFile() {
        return admissionFile;
    }

    public void setAdmissionFile(String admissionFile) {
        this.admissionFile = admissionFile;
    }

    public String getStudentPopulationFile() {
        return studentPopulationFile;
    }

    public void setStudentPopulationFile(String studentPopulationFile) {
        this.studentPopulationFile = studentPopulationFile;
    }
}
