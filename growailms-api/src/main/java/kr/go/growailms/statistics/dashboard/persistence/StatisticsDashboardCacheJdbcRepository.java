package kr.go.growailms.statistics.dashboard.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Repository
public class StatisticsDashboardCacheJdbcRepository {
    // 왜: 통계 대시보드는 같은 조건으로 반복 조회가 많아서, "최종 계산 결과 JSON"을 DB에 저장해 재사용합니다.
    private static final Logger log = LoggerFactory.getLogger(StatisticsDashboardCacheJdbcRepository.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public StatisticsDashboardCacheJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        createTableIfNotExists();
    }

    public Optional<String> findPayloadJson(
            String cacheType,
            String campus,
            String admCd,
            String admNm,
            Integer statsYear
    ) {
        String cacheKey = buildCacheKey(cacheType, campus, admCd, admNm, statsYear);

        String sql = """
                SELECT payload_json
                FROM statistics_dashboard_cache
                WHERE cache_key = :cacheKey
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cacheKey", cacheKey);

        var rows = jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> rs.getString("payload_json")
        );

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        increaseHitCount(cacheKey);
        return Optional.ofNullable(rows.get(0));
    }

    public void upsertPayloadJson(
            String cacheType,
            String campus,
            String admCd,
            String admNm,
            Integer statsYear,
            String payloadJson
    ) {
        String cacheKey = buildCacheKey(cacheType, campus, admCd, admNm, statsYear);
        String now = LocalDateTime.now().toString();

        String sql = """
                INSERT INTO statistics_dashboard_cache (
                    cache_key,
                    cache_type,
                    campus,
                    adm_cd,
                    adm_nm,
                    stats_year,
                    payload_json,
                    hit_count,
                    created_at,
                    updated_at
                )
                VALUES (
                    :cacheKey,
                    :cacheType,
                    :campus,
                    :admCd,
                    :admNm,
                    :statsYear,
                    :payloadJson,
                    0,
                    :now,
                    :now
                )
                ON DUPLICATE KEY UPDATE
                    payload_json = VALUES(payload_json),
                    updated_at = VALUES(updated_at)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cacheKey", cacheKey)
                .addValue("cacheType", cacheType)
                .addValue("campus", campus)
                .addValue("admCd", admCd)
                .addValue("admNm", admNm)
                .addValue("statsYear", statsYear)
                .addValue("payloadJson", payloadJson)
                .addValue("now", now);

        jdbcTemplate.update(sql, params);
    }

    private void increaseHitCount(String cacheKey) {
        String sql = """
                UPDATE statistics_dashboard_cache
                SET hit_count = hit_count + 1,
                    updated_at = :now
                WHERE cache_key = :cacheKey
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cacheKey", cacheKey)
                .addValue("now", LocalDateTime.now().toString());

        jdbcTemplate.update(sql, params);
    }

    private String buildCacheKey(
            String cacheType,
            String campus,
            String admCd,
            String admNm,
            Integer statsYear
    ) {
        // 왜: 파라미터 길이가 길어져도 PK 길이를 안정적으로 유지하기 위해 SHA-256 해시 키를 사용합니다.
        String rawKey = String.join("|",
                safe(cacheType),
                safe(campus),
                safe(admCd),
                safe(admNm),
                statsYear == null ? "" : String.valueOf(statsYear)
        );
        return safe(cacheType) + ":" + sha256(rawKey);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 해시 생성에 실패했습니다.", e);
        }
    }

    private void createTableIfNotExists() {
        // 왜: 로컬/개발 환경에서 DDL 누락으로 기능이 막히지 않도록, 캐시 테이블은 시작 시 최소 스키마를 보장합니다.
        String ddl = """
                CREATE TABLE IF NOT EXISTS statistics_dashboard_cache (
                    cache_key VARCHAR(128) NOT NULL,
                    cache_type VARCHAR(32) NOT NULL,
                    campus VARCHAR(255) NULL,
                    adm_cd VARCHAR(32) NULL,
                    adm_nm VARCHAR(255) NULL,
                    stats_year INT NULL,
                    payload_json LONGTEXT NOT NULL,
                    hit_count BIGINT NOT NULL DEFAULT 0,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL,
                    PRIMARY KEY (cache_key),
                    KEY idx_statistics_dashboard_cache_type_year (cache_type, stats_year)
                )
                """;
        try {
            jdbcTemplate.getJdbcTemplate().execute(ddl);
        } catch (Exception e) {
            log.error("통계 대시보드 캐시 테이블 생성 실패", e);
            throw e;
        }
    }
}
