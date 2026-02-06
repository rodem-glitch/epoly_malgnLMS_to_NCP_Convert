package kr.go.growailms.statistics.dashboard.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MemberKeyPopulationJdbcRepository {
    // 왜: 학번 기반 통계는 연도/캠퍼스/과정구분 단위 집계가 핵심이라, 복잡한 가공보다 SQL 집계로 바로 가져오는 편이 안전합니다.

    private static final Logger log = LoggerFactory.getLogger(MemberKeyPopulationJdbcRepository.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MemberKeyPopulationJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<YearCampusCourseCount> findYearCampusCourseCounts(String campusFilter) {
        String sql = """
            SELECT
              CAST(
                CONCAT(
                  CASE
                    WHEN CAST(SUBSTRING(TRIM(m.MEMBER_KEY), 1, 2) AS UNSIGNED) <= MOD(YEAR(CURDATE()), 100)
                      THEN '20'
                    ELSE '19'
                  END,
                  SUBSTRING(TRIM(m.MEMBER_KEY), 1, 2)
                ) AS UNSIGNED
              ) AS stat_year,
              TRIM(m.CAMPUS_CODE) AS campus_code,
              TRIM(m.CAMPUS_NAME) AS campus_name,
              SUBSTRING(TRIM(m.MEMBER_KEY), 5, 2) AS course_code,
              COUNT(DISTINCT TRIM(m.MEMBER_KEY)) AS member_count
            FROM LM_POLY_MEMBER m
            WHERE m.MEMBER_KEY IS NOT NULL
              AND LENGTH(TRIM(m.MEMBER_KEY)) = 10
              AND TRIM(m.MEMBER_KEY) BETWEEN '0000000000' AND '9999999999'
              AND m.CAMPUS_CODE IS NOT NULL
              AND TRIM(m.CAMPUS_CODE) <> ''
              AND m.CAMPUS_NAME IS NOT NULL
              AND TRIM(m.CAMPUS_NAME) <> ''
              -- 왜: 통합학번 규칙(년도2+캠퍼스2+과정2+일련4)에 맞는 2자리 과정구분만 집계해
              --     과정별 그래프/표 해석이 틀어지지 않게 합니다.
              AND SUBSTRING(TRIM(m.MEMBER_KEY), 5, 2) BETWEEN '00' AND '99'
              AND (
                    :campusFilter IS NULL
                    OR TRIM(REPLACE(m.CAMPUS_NAME, '캠퍼스', '')) = :campusFilter
                    OR TRIM(m.CAMPUS_NAME) = :campusFilter
                  )
            GROUP BY stat_year, campus_code, campus_name, course_code
            ORDER BY stat_year ASC, campus_code ASC, campus_name ASC, course_code ASC
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("campusFilter", campusFilter);

        log.info("학번 기반 인구 SQL 시작(과정구분 포함): campusFilter={}", campusFilter);

        try {
            List<YearCampusCourseCount> rows = jdbcTemplate.query(
                    sql,
                    params,
                    (rs, rowNum) -> new YearCampusCourseCount(
                            rs.getInt("stat_year"),
                            rs.getString("campus_code"),
                            rs.getString("campus_name"),
                            rs.getString("course_code"),
                            rs.getLong("member_count")
                    )
            );
            log.info("학번 기반 인구 SQL 완료: campusFilter={}, rowCount={}", campusFilter, rows.size());
            return rows;
        } catch (RuntimeException e) {
            Throwable rootCause = e;
            while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
                rootCause = rootCause.getCause();
            }
            log.error("학번 기반 인구 SQL 실패: campusFilter={}, rootCause={}", campusFilter, rootCause.getMessage(), e);
            throw e;
        }
    }

    public record YearCampusCourseCount(
            int year,
            String campusCode,
            String campusName,
            String courseCode,
            long memberCount
    ) {
    }
}
