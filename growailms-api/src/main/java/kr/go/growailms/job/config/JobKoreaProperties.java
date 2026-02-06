package kr.go.growailms.job.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "jobkorea")
public class JobKoreaProperties {
    // 왜: 잡코리아 API는 파라미터/엔드포인트가 프로젝트마다 달라져서 설정으로 제어합니다.

    private boolean enabled = true;
    private String apiUrl;
    private String apiKey;
    private String oemCode;
    private String apiKeyParam = "api";
    private String oemCodeParam = "Oem_Code";
    private String pageParam = "Page";
    // 왜: 잡코리아 가이드(v4.1) 기준으로 페이지당 건수 파라미터는 Size를 사용합니다.
    private String displayParam = "Size";
    // 왜: 잡코리아 가이드(v4.1) 기준으로 지역 파라미터는 area(예: I010)를 사용합니다.
    private String regionParam = "area";
    // 왜: 잡코리아 가이드(v4.1) 기준으로 업직종 대분류 파라미터는 rbcd를 사용합니다.
    private String industryParam = "rbcd";
    // 왜: 잡코리아 가이드(v4.1) 기준으로 업직종 소분류 파라미터는 rpcd(예: 1000001)를 사용합니다.
    private String occupationParam = "rpcd";
    // 왜: 잡코리아 가이드(v4.1) 기준으로 급여 필터는 pay/payterm 파라미터를 사용합니다.
    private String payParam = "pay";
    private String payTermParam = "payterm";
    // 왜: 잡코리아 가이드(v4.1) 기준으로 학력 필터는 edu1/edu3(학력무관 포함) 파라미터를 사용합니다.
    private String edu1Param = "edu1";
    private String edu3Param = "edu3";
    private Map<String, String> params = new LinkedHashMap<>();
    private Cache cache = new Cache();
    private Reclassification reclassification = new Reclassification();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getOemCode() {
        return oemCode;
    }

    public void setOemCode(String oemCode) {
        this.oemCode = oemCode;
    }

    public String getApiKeyParam() {
        return apiKeyParam;
    }

    public void setApiKeyParam(String apiKeyParam) {
        this.apiKeyParam = apiKeyParam;
    }

    public String getOemCodeParam() {
        return oemCodeParam;
    }

    public void setOemCodeParam(String oemCodeParam) {
        this.oemCodeParam = oemCodeParam;
    }

    public String getPageParam() {
        return pageParam;
    }

    public void setPageParam(String pageParam) {
        this.pageParam = pageParam;
    }

    public String getDisplayParam() {
        return displayParam;
    }

    public void setDisplayParam(String displayParam) {
        this.displayParam = displayParam;
    }

    public String getRegionParam() {
        return regionParam;
    }

    public void setRegionParam(String regionParam) {
        this.regionParam = regionParam;
    }

    public String getIndustryParam() {
        return industryParam;
    }

    public void setIndustryParam(String industryParam) {
        this.industryParam = industryParam;
    }

    public String getOccupationParam() {
        return occupationParam;
    }

    public void setOccupationParam(String occupationParam) {
        this.occupationParam = occupationParam;
    }

    public String getPayParam() {
        return payParam;
    }

    public void setPayParam(String payParam) {
        this.payParam = payParam;
    }

    public String getPayTermParam() {
        return payTermParam;
    }

    public void setPayTermParam(String payTermParam) {
        this.payTermParam = payTermParam;
    }

    public String getEdu1Param() {
        return edu1Param;
    }

    public void setEdu1Param(String edu1Param) {
        this.edu1Param = edu1Param;
    }

    public String getEdu3Param() {
        return edu3Param;
    }

    public void setEdu3Param(String edu3Param) {
        this.edu3Param = edu3Param;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public Reclassification getReclassification() {
        return reclassification;
    }

    public void setReclassification(Reclassification reclassification) {
        this.reclassification = reclassification;
    }

    public static class Cache {
        // 왜: 잡코리아 캐시도 호출량을 줄이기 위해 별도 설정으로 관리합니다.
        private boolean enabled = true;
        private long ttlMinutes = 1440;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getTtlMinutes() {
            return ttlMinutes;
        }

        public void setTtlMinutes(long ttlMinutes) {
            this.ttlMinutes = ttlMinutes;
        }
    }

    public static class Reclassification {
        // 왜: 통합(ALL)에서 잡코리아 공고를 표준 소분류로 "재분류 필터"할지 여부를 실험/튜닝하기 위해 분리합니다.
        // - 켜면: 품질이 좋아지지만(필터 정확도) 임베딩 호출량/지연이 늘 수 있습니다.
        // - 끄면: 임베딩 호출량이 줄어 빠르지만, 외부 분류(잡코리아 rpcd 추천)의 품질에 더 의존합니다.
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
