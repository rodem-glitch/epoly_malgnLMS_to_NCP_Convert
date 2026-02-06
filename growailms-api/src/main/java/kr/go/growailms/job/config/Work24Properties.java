package kr.go.growailms.job.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "work24")
public class Work24Properties {
    // 왜: Work24(워크넷) 호출 URL/키와 캐시 설정을 환경변수로 분리해서 운영/개발을 유연하게 맞춥니다.

    private String apiUrl;
    private String authKey;
    // 왜: 공통코드(지역/직종 등) 전용 API는 엔드포인트가 달라서 분리합니다.
    private String codeApiUrl;
    // 왜: 공통코드 API가 막힌 환경에서도 코드표(파일)로 DB를 재적재할 수 있어야 합니다.
    private String occupationCsvPath;
    private Cache cache = new Cache();

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getAuthKey() {
        return authKey;
    }

    public void setAuthKey(String authKey) {
        this.authKey = authKey;
    }

    public String getCodeApiUrl() {
        return codeApiUrl;
    }

    public void setCodeApiUrl(String codeApiUrl) {
        this.codeApiUrl = codeApiUrl;
    }

    public String getOccupationCsvPath() {
        return occupationCsvPath;
    }

    public void setOccupationCsvPath(String occupationCsvPath) {
        this.occupationCsvPath = occupationCsvPath;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public static class Cache {
        // 왜: 캐시를 켜면 갱신된 데이터를 DB에서 바로 내려줄 수 있어 API 호출을 줄일 수 있습니다.
        private boolean enabled = true;
        // 왜: 캐시 TTL을 0 이하로 두면 항상 실시간 조회로 동작하도록 할 수 있습니다.
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
}
