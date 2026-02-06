package kr.go.growailms.statistics.kosis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kosis")
public class KosisProperties {
    // 왜: 레거시(eGov)에서 쓰던 KOSIS 설정을 Spring Boot 방식으로 옮겨 관리합니다.

    private String consumerKey;
    private String consumerSecret;
    private String authUrl;
    private String populationUrl;
    private String industryCodeUrl;
    private String companyUrl;

    public String getConsumerKey() {
        return consumerKey;
    }

    public void setConsumerKey(String consumerKey) {
        this.consumerKey = consumerKey;
    }

    public String getConsumerSecret() {
        return consumerSecret;
    }

    public void setConsumerSecret(String consumerSecret) {
        this.consumerSecret = consumerSecret;
    }

    public String getAuthUrl() {
        return authUrl;
    }

    public void setAuthUrl(String authUrl) {
        this.authUrl = authUrl;
    }

    public String getPopulationUrl() {
        return populationUrl;
    }

    public void setPopulationUrl(String populationUrl) {
        this.populationUrl = populationUrl;
    }

    public String getIndustryCodeUrl() {
        return industryCodeUrl;
    }

    public void setIndustryCodeUrl(String industryCodeUrl) {
        this.industryCodeUrl = industryCodeUrl;
    }

    public String getCompanyUrl() {
        return companyUrl;
    }

    public void setCompanyUrl(String companyUrl) {
        this.companyUrl = companyUrl;
    }
}
