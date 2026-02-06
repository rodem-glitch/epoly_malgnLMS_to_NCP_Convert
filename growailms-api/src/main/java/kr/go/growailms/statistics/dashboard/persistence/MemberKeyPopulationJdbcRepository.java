package kr.go.growailms.statistics.dashboard.persistence;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Repository
public class MemberKeyPopulationJdbcRepository {
    // 왜: 학번(MEMBER_KEY) 규칙(년도2+캠퍼스2+과정2+일련4) 기반 통계를 만들기 위해
    //     학사 원천(COM.LMS_MEMBER_VIEW) 동기화 대상인 LM_POLY_MEMBER를 집계합니다.

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private static final Logger log = LoggerFactory.getLogger(MemberKeyPopulationJdbcRepository.class);

    public MemberKeyPopulationJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<YearCampusCount> findYearCampusCounts(String campusFilter) {
        String normalizedCampus = normalizeCampusFilter(campusFilter);
        log.info("학번 기반 인구 SQL 시작: campusFilter={}", normalizedCampus);

        // 왜: 운영/개발 DB 방언 차이(MySQL/H2 등)에서 문법 충돌을 줄이기 위해
        //     REGEXP/UNSIGNED/HAVING 별칭 의존을 제거하고, ANSI에 가까운 함수로만 집계합니다.
        String yearExpr = "CONCAT('20', SUBSTRING(TRIM(v.MEMBER_KEY), 1, 2))";
        String campusCodeExpr = "TRIM(v.CAMPUS_CODE)";
        String campusNameExpr = "TRIM(v.CAMPUS_NAME)";

        String sql = """
            SELECT
              %s AS stat_year,
              %s AS campus_code,
              %s AS campus_name,
              COUNT(DISTINCT v.MEMBER_KEY) AS member_count
            FROM LM_POLY_MEMBER v
            WHERE v.MEMBER_KEY IS NOT NULL
              AND LENGTH(TRIM(v.MEMBER_KEY)) = 10
              AND TRIM(v.MEMBER_KEY) BETWEEN '0000000000' AND '9999999999'
              AND v.CAMPUS_CODE IS NOT NULL
              AND TRIM(v.CAMPUS_CODE) <> ''
              AND v.CAMPUS_NAME IS NOT NULL
              AND TRIM(v.CAMPUS_NAME) <> ''
              AND (
                    :campusFilter IS NULL
                    OR TRIM(REPLACE(v.CAMPUS_NAME, '캠퍼스', '')) = :campusFilter
                    OR TRIM(v.CAMPUS_NAME) = :campusFilter
                  )
            GROUP BY %s, %s, %s
            ORDER BY %s ASC, %s ASC, %s ASC
            """.formatted(
                yearExpr,
                campusCodeExpr,
                campusNameExpr,
                yearExpr,
                campusCodeExpr,
                campusNameExpr,
                yearExpr,
                campusCodeExpr,
                campusNameExpr
        );

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("campusFilter", normalizedCampus);

        try {
            List<YearCampusCount> rows = jdbcTemplate.query(
                    sql,
                    params,
                    (rs, rowNum) -> new YearCampusCount(
                            parseYear(rs.getString("stat_year")),
                            rs.getString("campus_code"),
                            rs.getString("campus_name"),
                            rs.getLong("member_count")
                    )
            );
            log.info("학번 기반 인구 SQL 성공: campusFilter={}, rowCount={}", normalizedCampus, rows.size());
            return rows;
        } catch (Exception ex) {
            Throwable root = ex.getCause() == null ? ex : ex.getCause();
            log.error("학번 기반 인구 SQL 실패: campusFilter={}, rootCause={}", normalizedCampus, root.getMessage(), ex);
            throw ex;
        }
    }

    private int parseYear(String statYear) {
        if (!StringUtils.hasText(statYear)) {
            throw new IllegalStateException("학번 연도(stat_year)가 비어 있습니다.");
        }
        try {
            return Integer.parseInt(statYear.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("학번 연도(stat_year) 파싱 실패: " + statYear, e);
        }
    }

    private String normalizeCampusFilter(String campusFilter) {
        if (!StringUtils.hasText(campusFilter) || "전체".equals(campusFilter) || "전체 캠퍼스".equals(campusFilter)) {
            return null;
        }
        return campusFilter.trim();
    }

    public record YearCampusCount(
            int year,
            String campusCode,
            String campusName,
            long memberCount
    ) {
    }
}
