package kr.go.growailms.statistics.dashboard.service;

import kr.go.growailms.statistics.dashboard.persistence.MemberKeyPopulationJdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MemberKeyPopulationService {
    // 왜: 인구 탭에 "학번 규칙 기반(년도+캠퍼스) 분포"를 추가하려면
    //     LM_POLY_MEMBER(학사 원천 뷰 동기화본) 집계를 연도축 시리즈 형태로 가공해야 화면이 바로 그릴 수 있습니다.

    private static final Logger log = LoggerFactory.getLogger(MemberKeyPopulationService.class);

    private final MemberKeyPopulationJdbcRepository memberKeyPopulationJdbcRepository;

    public MemberKeyPopulationService(MemberKeyPopulationJdbcRepository memberKeyPopulationJdbcRepository) {
        this.memberKeyPopulationJdbcRepository = memberKeyPopulationJdbcRepository;
    }

    public MemberKeyPopulationResponse summarizeByYearAndCampus(String campus) {
        String normalizedCampus = normalizeCampus(campus);

        // 왜: 사용자 요구사항에 따라 학번 기반 그래프는 "캠퍼스"만 필터로 사용하고,
        //     행정구역/연도 필터는 집계에서 제외합니다.
        log.info("학번 기반 인구 통계 요청(캠퍼스 전용): campus={}", normalizedCampus);

        List<MemberKeyPopulationJdbcRepository.YearCampusCount> counts =
                memberKeyPopulationJdbcRepository.findYearCampusCounts(normalizedCampus);

        if (counts.isEmpty()) {
            log.warn("학번 기반 인구 통계 결과 없음(캠퍼스 전용): campus={}", normalizedCampus);
            return new MemberKeyPopulationResponse(
                    normalizedCampus,
                    List.of(),
                    List.of(),
                    List.of(),
                    0L
            );
        }

        List<Integer> years = buildYears(counts);
        List<CampusYearSeries> series = buildSeries(counts, years);
        List<YearCampusRow> rows = buildRows(counts);
        long totalMembers = rows.stream().mapToLong(YearCampusRow::memberCount).sum();

        log.info(
                "학번 기반 인구 통계 집계 완료: campus={}, years={}, campuses={}, rows={}, totalMembers={}",
                normalizedCampus, years.size(), series.size(), rows.size(), totalMembers
        );

        return new MemberKeyPopulationResponse(
                normalizedCampus,
                years,
                series,
                rows,
                totalMembers
        );
    }

    private List<Integer> buildYears(List<MemberKeyPopulationJdbcRepository.YearCampusCount> counts) {
        Map<Integer, Boolean> yearMap = new LinkedHashMap<>();
        for (MemberKeyPopulationJdbcRepository.YearCampusCount count : counts) {
            yearMap.putIfAbsent(count.year(), true);
        }
        return new ArrayList<>(yearMap.keySet());
    }

    private List<CampusYearSeries> buildSeries(
            List<MemberKeyPopulationJdbcRepository.YearCampusCount> counts,
            List<Integer> years
    ) {
        Map<String, String> campusNameMap = new LinkedHashMap<>();
        Map<String, String> campusCodeMap = new LinkedHashMap<>();
        Map<String, Map<Integer, Long>> campusYearCountMap = new LinkedHashMap<>();

        for (MemberKeyPopulationJdbcRepository.YearCampusCount count : counts) {
            String campusKey = buildCampusKey(count.campusCode(), count.campusName());
            campusCodeMap.putIfAbsent(campusKey, count.campusCode());
            campusNameMap.putIfAbsent(campusKey, count.campusName());

            Map<Integer, Long> yearCountMap = campusYearCountMap.computeIfAbsent(campusKey, key -> new LinkedHashMap<>());
            yearCountMap.put(count.year(), count.memberCount());
        }

        List<CampusYearSeries> result = new ArrayList<>();
        for (String campusKey : campusYearCountMap.keySet()) {
            Map<Integer, Long> yearCountMap = campusYearCountMap.get(campusKey);
            List<Long> yearCounts = new ArrayList<>();
            long totalCount = 0L;

            for (Integer year : years) {
                long count = yearCountMap.getOrDefault(year, 0L);
                yearCounts.add(count);
                totalCount += count;
            }

            result.add(new CampusYearSeries(
                    campusCodeMap.get(campusKey),
                    campusNameMap.get(campusKey),
                    yearCounts,
                    totalCount
            ));
        }
        return result;
    }

    private List<YearCampusRow> buildRows(List<MemberKeyPopulationJdbcRepository.YearCampusCount> counts) {
        List<YearCampusRow> rows = new ArrayList<>();
        for (MemberKeyPopulationJdbcRepository.YearCampusCount count : counts) {
            rows.add(new YearCampusRow(
                    count.year(),
                    count.campusCode(),
                    count.campusName(),
                    count.memberCount()
            ));
        }
        return rows;
    }

    private String buildCampusKey(String campusCode, String campusName) {
        return "%s::%s".formatted(campusCode, campusName);
    }

    private String normalizeCampus(String campus) {
        if (!StringUtils.hasText(campus)) {
            return null;
        }
        String normalized = campus.trim();
        if ("전체".equals(normalized) || "전체 캠퍼스".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    public record MemberKeyPopulationResponse(
            String campus,
            List<Integer> years,
            List<CampusYearSeries> campusSeries,
            List<YearCampusRow> rows,
            long totalMembers
    ) {
    }

    public record CampusYearSeries(
            String campusCode,
            String campusName,
            List<Long> yearCounts,
            long totalCount
    ) {
    }

    public record YearCampusRow(
            int year,
            String campusCode,
            String campusName,
            long memberCount
    ) {
    }
}
