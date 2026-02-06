package kr.go.growailms.statistics.dashboard.service;

import kr.go.growailms.statistics.dashboard.persistence.MemberKeyPopulationJdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MemberKeyPopulationService {
    // 왜: 학번 기반 인구 통계는 "캠퍼스 필터 1개"만 적용되는 별도 카드라서
    //     기존 인구/산업 통계와 분리해 단순한 응답 구조로 관리합니다.

    private static final Logger log = LoggerFactory.getLogger(MemberKeyPopulationService.class);

    private final MemberKeyPopulationJdbcRepository memberKeyPopulationJdbcRepository;

    public MemberKeyPopulationService(MemberKeyPopulationJdbcRepository memberKeyPopulationJdbcRepository) {
        this.memberKeyPopulationJdbcRepository = memberKeyPopulationJdbcRepository;
    }

    public MemberKeyPopulationResponse summarizeByYearAndCampus(String campus) {
        String normalizedCampus = normalizeCampus(campus);

        log.info("학번 기반 인구 통계 요청(캠퍼스 전용): campus={}", normalizedCampus);

        List<MemberKeyPopulationJdbcRepository.YearCampusCourseCount> rows =
                memberKeyPopulationJdbcRepository.findYearCampusCourseCounts(normalizedCampus);

        Set<Integer> yearSet = new LinkedHashSet<>();
        Map<String, CampusAccumulator> campusMap = new LinkedHashMap<>();
        Map<String, CourseAccumulator> courseMap = new LinkedHashMap<>();
        List<YearCampusRow> responseRows = new ArrayList<>();
        long totalMembers = 0L;

        for (MemberKeyPopulationJdbcRepository.YearCampusCourseCount row : rows) {
            yearSet.add(row.year());

            YearCampusRow responseRow = new YearCampusRow(
                    row.year(),
                    row.campusCode(),
                    row.campusName(),
                    row.courseCode(),
                    row.memberCount()
            );
            responseRows.add(responseRow);
            totalMembers += row.memberCount();

            String campusKey = (row.campusCode() == null ? "" : row.campusCode()) + "||" + (row.campusName() == null ? "" : row.campusName());
            CampusAccumulator acc = campusMap.computeIfAbsent(
                    campusKey,
                    key -> new CampusAccumulator(row.campusCode(), row.campusName())
            );
            long campusPrev = acc.yearCounts.getOrDefault(row.year(), 0L);
            acc.yearCounts.put(row.year(), campusPrev + row.memberCount());

            String courseCode = row.courseCode() == null ? "" : row.courseCode().trim();
            CourseAccumulator courseAccumulator = courseMap.computeIfAbsent(
                    courseCode,
                    key -> new CourseAccumulator(courseCode)
            );
            long coursePrev = courseAccumulator.yearCounts.getOrDefault(row.year(), 0L);
            courseAccumulator.yearCounts.put(row.year(), coursePrev + row.memberCount());
        }

        List<Integer> years = new ArrayList<>(yearSet);
        List<CampusYearSeries> campusSeries = new ArrayList<>();
        for (CampusAccumulator acc : campusMap.values()) {
            List<Long> yearCounts = new ArrayList<>();
            for (Integer year : years) {
                yearCounts.add(acc.yearCounts.getOrDefault(year, 0L));
            }
            campusSeries.add(new CampusYearSeries(acc.campusCode, acc.campusName, yearCounts));
        }

        List<CourseYearSeries> courseSeries = new ArrayList<>();
        for (CourseAccumulator acc : courseMap.values()) {
            List<Long> yearCounts = new ArrayList<>();
            for (Integer year : years) {
                yearCounts.add(acc.yearCounts.getOrDefault(year, 0L));
            }
            courseSeries.add(new CourseYearSeries(acc.courseCode, yearCounts));
        }

        log.info(
                "학번 기반 인구 통계 응답: campus={}, years={}, campusSeries={}, courseSeries={}, rows={}, totalMembers={}",
                normalizedCampus,
                years.size(),
                campusSeries.size(),
                courseSeries.size(),
                responseRows.size(),
                totalMembers
        );

        return new MemberKeyPopulationResponse(years, campusSeries, courseSeries, responseRows, totalMembers);
    }

    private String normalizeCampus(String campus) {
        if (!StringUtils.hasText(campus)) {
            return null;
        }

        String trimmedCampus = campus.trim();
        if ("전체".equals(trimmedCampus) || "전체 캠퍼스".equals(trimmedCampus)) {
            return null;
        }
        return trimmedCampus;
    }

    private static class CampusAccumulator {
        private final String campusCode;
        private final String campusName;
        private final Map<Integer, Long> yearCounts = new LinkedHashMap<>();

        private CampusAccumulator(String campusCode, String campusName) {
            this.campusCode = campusCode;
            this.campusName = campusName;
        }
    }

    private static class CourseAccumulator {
        private final String courseCode;
        private final Map<Integer, Long> yearCounts = new LinkedHashMap<>();

        private CourseAccumulator(String courseCode) {
            this.courseCode = courseCode;
        }
    }

    public record MemberKeyPopulationResponse(
            List<Integer> years,
            List<CampusYearSeries> campusSeries,
            List<CourseYearSeries> courseSeries,
            List<YearCampusRow> rows,
            long totalMembers
    ) {
    }

    public record CampusYearSeries(
            String campusCode,
            String campusName,
            List<Long> yearCounts
    ) {
    }

    public record CourseYearSeries(
            String courseCode,
            List<Long> yearCounts
    ) {
    }

    public record YearCampusRow(
            int year,
            String campusCode,
            String campusName,
            String courseCode,
            long memberCount
    ) {
    }
}
