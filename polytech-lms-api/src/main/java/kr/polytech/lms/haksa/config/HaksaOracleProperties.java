package kr.polytech.lms.haksa.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 왜: 폴리텍 학사 Oracle DB 접속 설정을 YAML에서 바인딩합니다.
 * haksa.oracle.url / username / password / hikari.* 에 대응합니다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "haksa.oracle")
public class HaksaOracleProperties {
    private String url;
    private String username;
    private String password;
    private String driverClassName = "oracle.jdbc.OracleDriver";
    private HikariSettings hikari = new HikariSettings();

    @Getter
    @Setter
    public static class HikariSettings {
        private int maximumPoolSize = 5;
        private int minimumIdle = 2;
        private long connectionTimeout = 30000;
        private String connectionTestQuery = "SELECT 1 FROM DUAL";
        private long keepaliveTime = 300000;
        private long maxLifetime = 1800000;
        private boolean readOnly = true;
        private String poolName = "haksa-oracle-pool";
    }
}
