package kr.go.growailms.statistics.student.persistence;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

@Repository
public class StudentStatisticsJdbcRepository {
    // 왜: LM_POLY_* 테이블은 레거시/미러 성격이 강해서, 복잡한 조인은 JPA보다 SQL로 명확히 작성하는 편이 안전합니다.

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public StudentStatisticsJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<YearTerm> findRecentYearTerms(int limit) {
        // 왜: 화면 필터에서 "기준 학기"를 선택해야 재학생(수강 중) 분포를 안정적으로 계산할 수 있습니다.
        String sql = """
            SELECT OPEN_YEAR AS year, OPEN_TERM AS term
              FROM LM_POLY_COURSE
             WHERE VISIBLE = 'Y'
             GROUP BY OPEN_YEAR, OPEN_TERM
             ORDER BY CAST(OPEN_YEAR AS UNSIGNED) DESC, CAST(OPEN_TERM AS UNSIGNED) DESC
             LIMIT :limit
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", Math.max(1, limit));

        return jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> new YearTerm(rs.getString("year"), rs.getString("term"))
        );
    }

    public List<CampusDeptCount> countEnrolledStudentsByCampusAndDept(String year, String term) {
        // 왜: "캠퍼스 학생비율(=전공/학과별 재학생 비율)" 계산은
        //     (캠퍼스, 학과)별 distinct 재학생 수가 기본 단위입니다.
        String sql = """
            SELECT
              TRIM(REPLACE(m.CAMPUS_NAME, '캠퍼스', '')) AS campus,
              TRIM(SUBSTRING_INDEX(m.DEPT_NAME, '(', 1)) AS dept,
              COUNT(DISTINCT s.MEMBER_KEY) AS student_count
            FROM LM_POLY_STUDENT s
            JOIN LM_POLY_MEMBER m
              ON m.MEMBER_KEY = s.MEMBER_KEY
            WHERE s.OPEN_YEAR = :year
              AND s.OPEN_TERM = :term
              AND s.VISIBLE = 'Y'
              AND m.CAMPUS_NAME IS NOT NULL AND m.CAMPUS_NAME <> ''
              AND m.DEPT_NAME IS NOT NULL AND m.DEPT_NAME <> ''
            GROUP BY campus, dept
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("year", year)
                .addValue("term", term);

        return jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> new CampusDeptCount(
                        rs.getString("campus"),
                        rs.getString("dept"),
                        rs.getLong("student_count")
                )
        );
    }

    public List<AgeBandCount> countEnrolledStudentsByAgeBand(String academicYear, String term, String campus, int baseYear) {
        // 왜: "연령대별 캠퍼스 학생비율"은 내부 데이터(TB_USER.BIRTHDAY)에 의존합니다.
        // - 매핑이 부족할 수 있으므로, 결과와 함께 표본 수(sampleSize)를 같이 보여주는 방식이 안전합니다.
        if (!StringUtils.hasText(campus)) {
            throw new IllegalArgumentException("캠퍼스가 비어 있습니다.");
        }

        String sql = """
            SELECT
              CASE
                WHEN (:baseYear - CAST(SUBSTRING(u.BIRTHDAY, 1, 4) AS SIGNED)) BETWEEN 10 AND 19 THEN '10대'
                WHEN (:baseYear - CAST(SUBSTRING(u.BIRTHDAY, 1, 4) AS SIGNED)) BETWEEN 20 AND 29 THEN '20대'
                WHEN (:baseYear - CAST(SUBSTRING(u.BIRTHDAY, 1, 4) AS SIGNED)) BETWEEN 30 AND 39 THEN '30대'
                WHEN (:baseYear - CAST(SUBSTRING(u.BIRTHDAY, 1, 4) AS SIGNED)) BETWEEN 40 AND 49 THEN '40대'
                WHEN (:baseYear - CAST(SUBSTRING(u.BIRTHDAY, 1, 4) AS SIGNED)) BETWEEN 50 AND 59 THEN '50대'
                WHEN (:baseYear - CAST(SUBSTRING(u.BIRTHDAY, 1, 4) AS SIGNED)) >= 60 THEN '60대 이상'
                ELSE '기타'
              END AS age_band,
              COUNT(DISTINCT s.MEMBER_KEY) AS student_count
            FROM LM_POLY_STUDENT s
            JOIN LM_POLY_MEMBER m
              ON m.MEMBER_KEY = s.MEMBER_KEY
            JOIN LM_POLY_MEMBER_KEY mk
              ON mk.MEMBER_KEY = m.MEMBER_KEY
            JOIN TB_USER u
              ON u.SITE_ID = 1
             AND (u.LOGIN_ID COLLATE utf8mb4_unicode_ci) = mk.ALIAS_KEY
            WHERE s.OPEN_YEAR = :academicYear
              AND s.OPEN_TERM = :term
              AND s.VISIBLE = 'Y'
              AND LENGTH(u.BIRTHDAY) = 8
              AND REPLACE(m.CAMPUS_NAME, '캠퍼스', '') = :campus
            GROUP BY age_band
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("academicYear", academicYear)
                .addValue("term", term)
                .addValue("campus", campus.trim())
                .addValue("baseYear", baseYear);

        return jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> new AgeBandCount(rs.getString("age_band"), rs.getLong("student_count"))
        );
    }

    public List<AgeBandGenderCount> countEnrolledStudentsByAgeBandAndGender(String academicYear, String term, String campus, int baseYear) {
        // 왜: PPTX 요구사항에 "연령대별 남녀 성비(행정구역 vs 캠퍼스)"가 포함되어 있어,
        //     캠퍼스 재학생도 같은 연령대 기준으로 남/여 비율을 계산할 수 있어야 합니다.
        //     TB_USER.GENDER(1=남성, 2=여성)를 사용합니다.
        if (!StringUtils.hasText(campus)) {
            throw new IllegalArgumentException("캠퍼스가 비어 있습니다.");
        }

        String sql = """
            SELECT
              CASE
                WHEN (:baseYear - CAST(SUBSTRING(u.BIRTHDAY, 1, 4) AS SIGNED)) BETWEEN 10 AND 19 THEN '10대'
                WHEN (:baseYear - CAST(SUBSTRING(u.BIRTHDAY, 1, 4) AS SIGNED)) BETWEEN 20 AND 29 THEN '20대'
                WHEN (:baseYear - CAST(SUBSTRING(u.BIRTHDAY, 1, 4) AS SIGNED)) BETWEEN 30 AND 39 THEN '30대'
                WHEN (:baseYear - CAST(SUBSTRING(u.BIRTHDAY, 1, 4) AS SIGNED)) BETWEEN 40 AND 49 THEN '40대'
                WHEN (:baseYear - CAST(SUBSTRING(u.BIRTHDAY, 1, 4) AS SIGNED)) BETWEEN 50 AND 59 THEN '50대'
                WHEN (:baseYear - CAST(SUBSTRING(u.BIRTHDAY, 1, 4) AS SIGNED)) >= 60 THEN '60대 이상'
                ELSE '기타'
              END AS age_band,
              u.GENDER AS gender,
              COUNT(DISTINCT s.MEMBER_KEY) AS student_count
            FROM LM_POLY_STUDENT s
            JOIN LM_POLY_MEMBER m
              ON m.MEMBER_KEY = s.MEMBER_KEY
            JOIN LM_POLY_MEMBER_KEY mk
              ON mk.MEMBER_KEY = m.MEMBER_KEY
            JOIN TB_USER u
              ON u.SITE_ID = 1
             AND (u.LOGIN_ID COLLATE utf8mb4_unicode_ci) = mk.ALIAS_KEY
            WHERE s.OPEN_YEAR = :academicYear
              AND s.OPEN_TERM = :term
              AND s.VISIBLE = 'Y'
              AND LENGTH(u.BIRTHDAY) = 8
              AND u.GENDER IN ('1', '2')
              AND REPLACE(m.CAMPUS_NAME, '캠퍼스', '') = :campus
            GROUP BY age_band, u.GENDER
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("academicYear", academicYear)
                .addValue("term", term)
                .addValue("campus", campus.trim())
                .addValue("baseYear", baseYear);

        return jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> new AgeBandGenderCount(
                        rs.getString("age_band"),
                        rs.getString("gender"),
                        rs.getLong("student_count")
                )
        );
    }

    public record YearTerm(String year, String term) {
    }

    public record CampusDeptCount(String campus, String dept, long studentCount) {
    }

    public record AgeBandCount(String ageBand, long studentCount) {
    }

    public record AgeBandGenderCount(String ageBand, String gender, long studentCount) {
    }
}
