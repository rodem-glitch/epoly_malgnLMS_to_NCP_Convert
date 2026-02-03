package kr.polytech.lms.statistics.dashboard.service;

import kr.polytech.lms.statistics.kosis.client.dto.KosisPopulationRow;
import kr.polytech.lms.statistics.kosis.service.KosisStatisticsService;
import kr.polytech.lms.statistics.sgis.service.SgisAdministrativeCodeService;
import kr.polytech.lms.statistics.student.excel.CampusStudentPopulationExcelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PopulationComparisonService {
    // 왜: 인구 탭은 "지역(행정구역) 인구 분포"와 "캠퍼스 학생(재학생) 분포"를 같은 연령대 축으로 비교하는 화면입니다.
    // - 지역 인구는 KOSIS(SGIS) API로,
    // - 캠퍼스 학생은 엑셀 기반으로 계산합니다. (내부 DB와 무관하게 동작해야 한다는 요구사항 반영)

    private static final List<AgeBand> AGE_BANDS = List.of(
            new AgeBand("10대", List.of("31")),
            new AgeBand("20대", List.of("32")),
            new AgeBand("30대", List.of("33")),
            new AgeBand("40대", List.of("34")),
            new AgeBand("50대", List.of("35")),
            // 왜: SGIS의 age_type=40은 70대 이상(70+) 집계이므로, 60대(36) + 70대 이상(40)으로 60대 이상을 구성합니다.
            new AgeBand("60대 이상", List.of("36", "40"))
    );

    private final KosisStatisticsService kosisStatisticsService;
    private final CampusStudentPopulationExcelService campusStudentPopulationExcelService;
    private final SgisAdministrativeCodeService sgisAdministrativeCodeService;

    private static final Logger log = LoggerFactory.getLogger(PopulationComparisonService.class);

    public PopulationComparisonService(
            KosisStatisticsService kosisStatisticsService,
            CampusStudentPopulationExcelService campusStudentPopulationExcelService,
            SgisAdministrativeCodeService sgisAdministrativeCodeService
    ) {
        this.kosisStatisticsService = kosisStatisticsService;
        this.campusStudentPopulationExcelService = campusStudentPopulationExcelService;
        this.sgisAdministrativeCodeService = sgisAdministrativeCodeService;
    }

    public PopulationComparisonResponse compare(
            String campus,
            String admCd,
            String admNm,
            Integer populationYear
    ) {
        if (!StringUtils.hasText(campus)) {
            throw new IllegalArgumentException("campus는 필수입니다.");
        }

        String resolvedCampus = campus.trim();
        int desiredPopulationYear = resolvePopulationYear(populationYear);

        String requestedAdmCd = normalizeRequestedAdmCd(admCd);
        String requestedAdmNm = normalizeRequestedAdmNm(admNm);

        // 왜: SGIS 인구 API는 행안부 코드(41/28/30...)가 아니라 SGIS 코드(31/23/25...) 체계를 씁니다.
        //     프론트는 캠퍼스 소속 행정구역을 "이름"까지 알고 있으므로, 이름 기반(stage API)으로 SGIS 코드를 찾아 변환합니다.
        SgisAdministrativeCodeService.Resolution sgisResolution =
                sgisAdministrativeCodeService.resolveToSgisAdmCd(requestedAdmCd, requestedAdmNm);

        String resolvedAdmCd = sgisResolution.sgisAdmCd();

        AdmCdResolution admCdResolution = resolveAdmCdAndYear(resolvedAdmCd, desiredPopulationYear);
        String usedAdmCd = admCdResolution.usedAdmCd();
        int usedPopulationYear = admCdResolution.populationYear();

        boolean admCdFallback = sgisResolution.mappingFallbackApplied() || admCdResolution.dataFallbackApplied();

        // 왜: "서울만 되고 나머지는 0" 같은 이슈는 대체로 admCd 변환/대체 단계에서 발생합니다.
        //     1회 요청 기준으로 요청/변환/사용 코드를 한 줄로 남겨두면, 실제 호출이 어떤 값으로 나갔는지 빠르게 확인할 수 있습니다.
        log.info("인구 통계 요청: campus={}, requestedAdmCd={}, requestedAdmNm={}, sgisAdmCd={}, usedAdmCd={}, year={}, fallback={}",
                resolvedCampus, requestedAdmCd, requestedAdmNm, resolvedAdmCd, usedAdmCd, usedPopulationYear, admCdFallback);

        Map<String, GenderCount> regionCounts = loadRegionPopulationGenderCounts(usedAdmCd, usedPopulationYear);
        long regionTotal = regionCounts.values().stream().mapToLong(GenderCount::total).sum();

        Map<String, GenderCount> campusCounts = loadCampusStudentAgeGenderCounts(resolvedCampus, usedPopulationYear);
        long campusTotal = campusCounts.values().stream().mapToLong(GenderCount::total).sum();

        List<AgeRow> rows = new ArrayList<>();
        for (AgeBand band : AGE_BANDS) {
            GenderCount region = regionCounts.getOrDefault(band.label(), GenderCount.empty());
            GenderCount campusCount = campusCounts.getOrDefault(band.label(), GenderCount.empty());

            double regionRatio = toPercent(region.total(), regionTotal);
            double campusRatio = toPercent(campusCount.total(), campusTotal);
            double gap = campusRatio - regionRatio;

            double regionMaleRatio = toPercent(region.male(), region.total());
            double campusMaleRatio = toPercent(campusCount.male(), campusCount.total());
            double maleGap = campusMaleRatio - regionMaleRatio;

            rows.add(new AgeRow(
                    band.label(),
                    region.total(),
                    regionRatio,
                    campusCount.total(),
                    campusRatio,
                    gap,
                    regionMaleRatio,
                    campusMaleRatio,
                    maleGap
            ));
        }

        return new PopulationComparisonResponse(
                resolvedCampus,
                usedAdmCd,
                requestedAdmCd,
                admCdFallback,
                usedPopulationYear,
                campusTotal,
                regionTotal,
                rows
        );
    }

    private Map<String, GenderCount> loadRegionPopulationGenderCounts(String admCd, int year) {
        Map<String, GenderCount> result = new LinkedHashMap<>();
        for (AgeBand band : AGE_BANDS) {
            long maleSum = 0L;
            long femaleSum = 0L;
            for (String ageType : band.ageTypes()) {
                maleSum += safeSumPopulation(year, ageType, "1", admCd);
                femaleSum += safeSumPopulation(year, ageType, "2", admCd);
            }
            result.put(band.label(), new GenderCount(maleSum, femaleSum));
        }
        return result;
    }

    private Map<String, GenderCount> loadCampusStudentAgeGenderCounts(String campus, int baseYear) {
        Map<String, GenderCount> empty = emptyGenderCounts();

        if (campusStudentPopulationExcelService.isEnabled()) {
            Map<String, CampusStudentPopulationExcelService.GenderCount> rows =
                    campusStudentPopulationExcelService.countByAgeBandAndGender(campus, null, null, baseYear);
            Map<String, GenderCount> result = new LinkedHashMap<>();
            for (AgeBand band : AGE_BANDS) {
                CampusStudentPopulationExcelService.GenderCount c = rows.getOrDefault(band.label(), CampusStudentPopulationExcelService.GenderCount.empty());
                result.put(band.label(), new GenderCount(c.male(), c.female()));
            }
            return result;
        }

        // 왜: 현재 요구사항은 "내부 DB와 무관"이므로, 엑셀 파일이 없으면 0으로 표시합니다(오류 대신 안전한 기본값).
        return empty;
    }

    private int resolvePopulationYear(Integer populationYear) {
        if (populationYear != null) {
            return populationYear;
        }
        // 왜: 기본 화면 기본값을 2024로 고정해 혼선을 줄입니다.
        return 2024;
    }

    private int resolveAvailablePopulationYear(int desiredYear, String admCd) {
        // 왜: KOSIS 인구 통계는 최신 연도가 바로 제공되지 않을 수 있어, 사용 가능한 연도를 자동 보정합니다.
        // - 예: 2024가 N/A면 2023으로 내려가서 조회
        for (int y = desiredYear; y >= desiredYear - 5; y--) {
            long sample = safeSumPopulation(y, "32", "0", admCd);
            if (sample > 0) {
                return y;
            }
        }
        return desiredYear;
    }

    private String normalizeRequestedAdmCd(String admCd) {
        // 왜: 인구 API에서 전국은 "adm_cd를 아예 보내지 않는 것"이 정상 동작이므로, 여기서는 null로 둡니다.
        if (!StringUtils.hasText(admCd) || "전체".equals(admCd)) {
            return null;
        }
        return admCd.trim();
    }

    private String normalizeRequestedAdmNm(String admNm) {
        if (!StringUtils.hasText(admNm) || "전체".equals(admNm)) {
            return null;
        }
        return admNm.trim();
    }

    private AdmCdResolution resolveAdmCdAndYear(String resolvedAdmCd, int desiredYear) {
        // 왜: 특정 행정구역 코드가 외부 API에 존재하지 않거나(또는 해당 연도 데이터가 없어) 0으로만 내려오는 경우가 있습니다.
        //      이때 화면이 전부 0으로 깨져 보이므로, "한 단계 상위 행정구역"으로 순차 대체해서 데이터가 존재하는 코드를 찾습니다.
        List<String> candidates = buildAdmCdFallbackCandidates(resolvedAdmCd);

        for (String candidateAdmCd : candidates) {
            int candidateYear = resolveAvailablePopulationYear(desiredYear, candidateAdmCd);
            long sample = safeSumPopulation(candidateYear, "32", "0", candidateAdmCd);
            if (sample > 0) {
                boolean dataFallbackApplied = !equalsNullable(candidateAdmCd, resolvedAdmCd);
                if (dataFallbackApplied) {
                    log.info("인구 통계 행정구역 데이터 대체: resolvedAdmCd={}, usedAdmCd={}, year={}", resolvedAdmCd, candidateAdmCd, candidateYear);
                }
                return new AdmCdResolution(resolvedAdmCd, candidateAdmCd, dataFallbackApplied, candidateYear);
            }
        }

        // 여기까지 왔다면(전국까지) 데이터가 없다는 뜻이라, 원인 파악을 위해 경고 로그를 남깁니다.
        log.warn("인구 통계 행정구역 데이터 없음(대체 실패): resolvedAdmCd={}, desiredYear={}", resolvedAdmCd, desiredYear);
        return new AdmCdResolution(resolvedAdmCd, resolvedAdmCd, false, desiredYear);
    }

    private List<String> buildAdmCdFallbackCandidates(String admCd) {
        // 왜: 7자리(읍면동) → 5자리(시군구) → 2자리(시도) → 전국(=adm_cd 미전달) 순서로 대체합니다.
        Set<String> candidates = new LinkedHashSet<>();

        String current = StringUtils.hasText(admCd) ? admCd.trim() : null;
        candidates.add(current);

        if (StringUtils.hasText(current) && current.matches("\\d+")) {
            while (current.length() > 2) {
                if (current.length() > 5) {
                    current = current.substring(0, 5);
                } else {
                    current = current.substring(0, 2);
                }
                candidates.add(current);
            }
        }

        // 전국(=adm_cd 미전달)
        candidates.add(null);
        return List.copyOf(candidates);
    }

    private double toPercent(long part, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return (part * 100.0) / total;
    }

    private boolean equalsNullable(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    private long sumPopulation(List<KosisPopulationRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0L;
        }
        return rows.stream().mapToLong(KosisPopulationRow::getPopulation).sum();
    }

    private long safeSumPopulation(int year, String ageType, String gender, String admCd) {
        // 왜: 외부 API(KOSIS)는 키/토큰/장애 등으로 실패할 수 있습니다.
        //     화면 전체가 500으로 깨지지 않도록, 실패는 0으로 처리하고 나머지(캠퍼스 학생 분포)는 계속 보여줍니다.
        try {
            return sumPopulation(kosisStatisticsService.getPopulation(String.valueOf(year), ageType, gender, admCd));
        } catch (Exception e) {
            log.warn("KOSIS 인구 조회 실패(폴백=0): year={}, ageType={}, gender={}, admCd={}", year, ageType, gender, admCd, e);
            return 0L;
        }
    }

    private Map<String, GenderCount> emptyGenderCounts() {
        Map<String, GenderCount> empty = new LinkedHashMap<>();
        for (AgeBand band : AGE_BANDS) {
            empty.put(band.label(), GenderCount.empty());
        }
        return empty;
    }

    private record AgeBand(String label, List<String> ageTypes) {
    }

    public record PopulationComparisonResponse(
            String campus,
            String admCd,
            String requestedAdmCd,
            boolean admCdFallback,
            int populationYear,
            long campusStudentSampleSize,
            long regionPopulationTotal,
            List<AgeRow> rows
    ) {
    }

    public record AgeRow(
            String ageBand,
            long regionCount,
            double regionRatio,
            long campusCount,
            double campusRatio,
            double gap,
            double regionMaleRatio,
            double campusMaleRatio,
            double maleGap
    ) {
    }

    private record GenderCount(long male, long female) {
        static GenderCount empty() {
            return new GenderCount(0L, 0L);
        }

        long total() {
            return male + female;
        }
    }

    private record AdmCdResolution(
            String resolvedAdmCd,
            String usedAdmCd,
            boolean dataFallbackApplied,
            int populationYear
    ) {
    }
}
