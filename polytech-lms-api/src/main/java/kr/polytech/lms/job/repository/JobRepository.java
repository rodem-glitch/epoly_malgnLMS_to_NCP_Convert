package kr.polytech.lms.job.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DataAccessException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JobRepository {
    // 왜: 코드 테이블/채용 캐시를 간단한 SQL로 조회하기 위해 JdbcTemplate을 사용합니다.

    private final JdbcTemplate jdbcTemplate;

    public JobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    public List<JobRegionCodeRow> findRegionCodes(String depthType, String depth1) {
        String safeDepthType = normalizeDepthType(depthType, "1");
        List<Object> params = new ArrayList<>();

        StringBuilder sql = new StringBuilder("""
            SELECT idx, depth1, depth2, depth3
            FROM regioncode
            WHERE 1=1
            """);

        appendDepthFilter(sql, params, safeDepthType);

        if (depth1 != null && !depth1.isBlank()) {
            sql.append(" AND depth1 = ?");
            params.add(depth1.trim());
        }

        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
            String title = resolveDepthTitle(safeDepthType, rs.getString("depth1"), rs.getString("depth2"), rs.getString("depth3"));
            return new JobRegionCodeRow(
                rs.getInt("idx"),
                title,
                rs.getString("depth1"),
                rs.getString("depth2"),
                rs.getString("depth3")
            );
        });
    }

    public List<JobOccupationCodeRow> findOccupationCodes(String depthType, String depth1, String depth2) {
        String safeDepthType = normalizeDepthType(depthType, "2");
        String safeDepth1 = trimToNull(depth1);
        String safeDepth2 = trimToNull(depth2);
        List<Object> params = new ArrayList<>();

        StringBuilder sql = new StringBuilder("""
            SELECT idx, code, parent_code, depth1, depth2, depth3
            FROM occupationcode
            WHERE 1=1
            """);

        switch (safeDepthType) {
            case "1" -> {
                // 왜: 대분류는 parent_code가 비어 있어야 합니다.
                sql.append(" AND (parent_code IS NULL OR parent_code = '')");
                sql.append(" AND depth1 IS NOT NULL AND depth1 <> ''");
                sql.append(" AND (depth2 IS NULL OR depth2 = '')");
                sql.append(" AND (depth3 IS NULL OR depth3 = '')");
            }
            case "2" -> {
                // 왜: 중분류는 선택한 대분류(code)를 parent_code로 가지고 있습니다.
                if (safeDepth1 != null) {
                    sql.append(" AND parent_code = ?");
                    params.add(safeDepth1);
                } else {
                    // 왜: 기존 화면(단일 셀렉트) 호환을 위해 대분류 미지정 시 "전체 중분류"를 내려줍니다.
                    sql.append(" AND parent_code IS NOT NULL AND parent_code <> ''");
                }
                sql.append(" AND depth2 IS NOT NULL AND depth2 <> ''");
                sql.append(" AND (depth3 IS NULL OR depth3 = '')");
            }
            case "3" -> {
                // 왜: 소분류는 선택한 중분류(code)를 parent_code로 가지고 있습니다.
                if (safeDepth2 == null) return List.of();
                sql.append(" AND parent_code = ?");
                params.add(safeDepth2);
                sql.append(" AND depth3 IS NOT NULL AND depth3 <> ''");
            }
            default -> {
                sql.append(" AND (parent_code IS NULL OR parent_code = '')");
                sql.append(" AND depth1 IS NOT NULL AND depth1 <> ''");
                sql.append(" AND (depth2 IS NULL OR depth2 = '')");
                sql.append(" AND (depth3 IS NULL OR depth3 = '')");
            }
        }

        sql.append(" ORDER BY code");

        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
            String title = resolveDepthTitle(safeDepthType, rs.getString("depth1"), rs.getString("depth2"), rs.getString("depth3"));
            return new JobOccupationCodeRow(
                rs.getInt("idx"),
                rs.getString("code"),
                title,
                rs.getString("depth1"),
                rs.getString("depth2"),
                rs.getString("depth3")
            );
        });
    }

    public List<String> findJobKoreaOccupationCodesByWork24Code(String work24Code) {
        String code = trimToNull(work24Code);
        if (code == null) return List.of();

        try {
            return jdbcTemplate.query("""
                SELECT jobkorea_code
                FROM job_work24_jobkorea_occupation_map
                WHERE work24_code = ?
                ORDER BY jobkorea_code
                """, new Object[]{code}, (rs, rowNum) -> rs.getString("jobkorea_code"));
        } catch (DataAccessException e) {
            // 왜: 운영/개발 DB에 테이블 또는 매핑 데이터가 아직 없을 수 있어,
            //      통합 조회가 예외로 터지지 않게 빈 목록으로 처리합니다.
            return List.of();
        }
    }

    public List<OccupationDepth3VectorRow> findAllOccupationDepth3VectorRows() {
        // 왜: 직무 소분류(표준 코드) 벡터 색인을 만들기 위해, 대/중/소 코드 정보를 한 번에 조회합니다.
        // - depth3(소분류)는 d3.code(6자리)이고
        // - depth2(중분류)는 d3.parent_code
        // - depth1(대분류)는 d2.parent_code 입니다.
        try {
            return jdbcTemplate.query("""
                SELECT
                    d3.code AS depth3_code,
                    d3.parent_code AS depth2_code,
                    d2.parent_code AS depth1_code,
                    d3.depth1 AS depth1_name,
                    d3.depth2 AS depth2_name,
                    d3.depth3 AS depth3_name
                FROM occupationcode d3
                LEFT JOIN occupationcode d2 ON d2.code = d3.parent_code
                WHERE d3.depth3 IS NOT NULL AND d3.depth3 <> ''
                ORDER BY d3.code
                """, (rs, rowNum) -> new OccupationDepth3VectorRow(
                rs.getString("depth1_code"),
                rs.getString("depth2_code"),
                rs.getString("depth3_code"),
                rs.getString("depth1_name"),
                rs.getString("depth2_name"),
                rs.getString("depth3_name")
            ));
        } catch (DataAccessException e) {
            // 왜: 운영 DB 스키마가 다르거나 테이블이 없으면 기능 전체가 죽지 않게 빈 목록으로 처리합니다.
            return List.of();
        }
    }

    public Optional<OccupationLookupRow> findOccupationLookupByCode(String occupationCode) {
        String code = trimToNull(occupationCode);
        if (code == null) return Optional.empty();

        try {
            List<OccupationLookupRow> rows = jdbcTemplate.query("""
                SELECT code, parent_code, depth1, depth2, depth3
                FROM occupationcode
                WHERE code = ?
                """, new Object[]{code}, (rs, rowNum) -> new OccupationLookupRow(
                rs.getString("code"),
                rs.getString("parent_code"),
                rs.getString("depth1"),
                rs.getString("depth2"),
                rs.getString("depth3")
            ));
            return rows.stream().findFirst();
        } catch (DataAccessException e) {
            // 왜: 운영 DB 스키마가 다르거나 테이블이 없으면 기능 전체가 죽지 않게 빈 값으로 처리합니다.
            return Optional.empty();
        }
    }

    public Optional<JobRecruitCacheRow> findRecruitCache(String queryKey, String provider) {
        try {
            List<JobRecruitCacheRow> rows = jdbcTemplate.query("""
                SELECT total, start_page, `display`, payload_json, updated_at
                FROM job_recruit_cache
                WHERE query_key = ? AND provider = ?
                """, new Object[]{queryKey, normalizeProvider(provider)}, (rs, rowNum) -> new JobRecruitCacheRow(
                rs.getInt("total"),
                rs.getInt("start_page"),
                rs.getInt("display"),
                rs.getString("payload_json"),
                toLocalDateTime(rs.getTimestamp("updated_at"))
            ));
            return rows.stream().findFirst();
        } catch (DataAccessException e) {
            // 왜: provider 컬럼이 아직 없으면 기존 테이블을 조회해도 동작해야 합니다.
            try {
                List<JobRecruitCacheRow> rows = jdbcTemplate.query("""
                    SELECT total, start_page, `display`, payload_json, updated_at
                    FROM job_recruit_cache
                    WHERE query_key = ?
                    """, new Object[]{queryKey}, (rs, rowNum) -> new JobRecruitCacheRow(
                    rs.getInt("total"),
                    rs.getInt("start_page"),
                    rs.getInt("display"),
                    rs.getString("payload_json"),
                    toLocalDateTime(rs.getTimestamp("updated_at"))
                ));
                return rows.stream().findFirst();
            } catch (DataAccessException ignored) {
                return Optional.empty();
            }
        }
    }

    public List<JobRecruitCacheKey> findAllRecruitCacheKeys() {
        try {
            return jdbcTemplate.query("""
                SELECT query_key, provider, region_code, occupation_code, start_page, `display`
                FROM job_recruit_cache
                """, (rs, rowNum) -> new JobRecruitCacheKey(
                rs.getString("query_key"),
                rs.getString("provider"),
                rs.getString("region_code"),
                rs.getString("occupation_code"),
                rs.getInt("start_page"),
                rs.getInt("display")
            ));
        } catch (DataAccessException e) {
            // 왜: provider 컬럼이 없으면 기존 컬럼으로라도 갱신 목록을 구성합니다.
            try {
                return jdbcTemplate.query("""
                    SELECT query_key, region_code, occupation_code, start_page, `display`
                    FROM job_recruit_cache
                    """, (rs, rowNum) -> new JobRecruitCacheKey(
                    rs.getString("query_key"),
                    "WORK24",
                    rs.getString("region_code"),
                    rs.getString("occupation_code"),
                    rs.getInt("start_page"),
                    rs.getInt("display")
                ));
            } catch (DataAccessException ignored) {
                return List.of();
            }
        }
    }

    public void upsertRecruitCache(JobRecruitCacheRow row, JobRecruitCacheKey key) {
        try {
            jdbcTemplate.update("""
                INSERT INTO job_recruit_cache
                    (query_key, provider, region_code, occupation_code, start_page, `display`, total, payload_json, updated_at)
                VALUES
                    (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    provider = VALUES(provider),
                    region_code = VALUES(region_code),
                    occupation_code = VALUES(occupation_code),
                    start_page = VALUES(start_page),
                    `display` = VALUES(`display`),
                    total = VALUES(total),
                    payload_json = VALUES(payload_json),
                    updated_at = VALUES(updated_at)
                """,
                key.queryKey(),
                normalizeProvider(key.provider()),
                key.regionCode(),
                key.occupationCode(),
                key.startPage(),
                key.display(),
                row.total(),
                row.payloadJson(),
                Timestamp.valueOf(row.updatedAt())
            );
        } catch (DataAccessException e) {
            // 왜: provider 컬럼이 없는 구버전 테이블도 동작할 수 있어야 합니다.
            try {
                jdbcTemplate.update("""
                    INSERT INTO job_recruit_cache
                        (query_key, region_code, occupation_code, start_page, `display`, total, payload_json, updated_at)
                    VALUES
                        (?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        region_code = VALUES(region_code),
                        occupation_code = VALUES(occupation_code),
                        start_page = VALUES(start_page),
                        `display` = VALUES(`display`),
                        total = VALUES(total),
                        payload_json = VALUES(payload_json),
                        updated_at = VALUES(updated_at)
                    """,
                    key.queryKey(),
                    key.regionCode(),
                    key.occupationCode(),
                    key.startPage(),
                    key.display(),
                    row.total(),
                    row.payloadJson(),
                    Timestamp.valueOf(row.updatedAt())
                );
            } catch (DataAccessException ignored) {
                // 왜: 캐시 저장 실패가 조회 기능 자체를 막으면 안 됩니다(실시간 조회로라도 응답).
            }
        }
    }

    private static void appendDepthFilter(StringBuilder sql, List<Object> params, String depthType) {
        switch (depthType) {
            // 왜: 코드 테이블이 NULL로 들어간 경우가 있어 NULL도 함께 걸러야 정상적으로 목록이 나옵니다.
            case "1" -> {
                sql.append(" AND depth1 IS NOT NULL AND depth1 <> ''");
                sql.append(" AND (depth2 IS NULL OR depth2 = '')");
                sql.append(" AND (depth3 IS NULL OR depth3 = '')");
            }
            case "2" -> {
                sql.append(" AND depth2 IS NOT NULL AND depth2 <> ''");
                sql.append(" AND (depth3 IS NULL OR depth3 = '')");
            }
            case "3" -> sql.append(" AND depth3 IS NOT NULL AND depth3 <> ''");
            default -> {
                sql.append(" AND depth1 IS NOT NULL AND depth1 <> ''");
            }
        }
    }

    public void replaceRegionCodes(List<RegionCodeInsertRow> rows) {
        if (rows == null || rows.isEmpty()) return;
        jdbcTemplate.update("DELETE FROM regioncode");

        jdbcTemplate.batchUpdate(
            "INSERT INTO regioncode (idx, depth1, depth2, depth3) VALUES (?, ?, ?, ?)",
            rows,
            rows.size(),
            (ps, row) -> {
                ps.setObject(1, row.idx());
                ps.setString(2, row.depth1());
                ps.setString(3, row.depth2());
                ps.setString(4, row.depth3());
            }
        );
    }

    public void replaceOccupationCodes(List<OccupationCodeInsertRow> rows) {
        if (rows == null || rows.isEmpty()) return;
        jdbcTemplate.update("DELETE FROM occupationcode");

        jdbcTemplate.batchUpdate(
            "INSERT INTO occupationcode (code, parent_code, depth1, depth2, depth3) VALUES (?, ?, ?, ?, ?)",
            rows,
            rows.size(),
            (ps, row) -> {
                ps.setString(1, row.code());
                ps.setString(2, row.parentCode());
                ps.setString(3, row.depth1());
                ps.setString(4, row.depth2());
                ps.setString(5, row.depth3());
            }
        );
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String resolveDepthTitle(String depthType, String depth1, String depth2, String depth3) {
        return switch (depthType) {
            case "1" -> depth1;
            case "2" -> depth2;
            case "3" -> depth3;
            default -> depth1;
        };
    }

    private static String normalizeDepthType(String depthType, String defaultValue) {
        if (depthType == null || depthType.isBlank()) return defaultValue;
        String trimmed = depthType.trim();
        return switch (trimmed) {
            case "1", "2", "3" -> trimmed;
            default -> defaultValue;
        };
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        if (timestamp == null) return LocalDateTime.MIN;
        return timestamp.toLocalDateTime();
    }

    private static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) return "WORK24";
        return provider.trim().toUpperCase();
    }

    public record JobRegionCodeRow(int idx, String title, String depth1, String depth2, String depth3) {
    }

    public record JobOccupationCodeRow(int idx, String code, String title, String depth1, String depth2, String depth3) {
    }

    public record JobRecruitCacheRow(int total, int startPage, int display, String payloadJson, LocalDateTime updatedAt) {
    }

    public record JobRecruitCacheKey(
        String queryKey,
        String provider,
        String regionCode,
        String occupationCode,
        int startPage,
        int display
    ) {
    }

    public record RegionCodeInsertRow(Integer idx, String depth1, String depth2, String depth3) {
    }

    public record OccupationCodeInsertRow(String code, String parentCode, String depth1, String depth2, String depth3) {
    }

    public record OccupationDepth3VectorRow(
        String depth1Code,
        String depth2Code,
        String depth3Code,
        String depth1Name,
        String depth2Name,
        String depth3Name
    ) {
    }

    public record OccupationLookupRow(
        String code,
        String parentCode,
        String depth1,
        String depth2,
        String depth3
    ) {
    }
}
