package kr.polytech.lms.statistics.dashboard.service;

import kr.polytech.lms.statistics.mapping.MajorIndustryMappingService;
import kr.polytech.lms.statistics.sgis.service.SgisCompanyCacheService;
import kr.polytech.lms.statistics.sgis.service.SgisAdministrativeCodeService;
import kr.polytech.lms.statistics.student.excel.CampusStudentQuotaExcelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class IndustryAnalysisService {
    // 왜: 화면(산업분포 분석)은
    //  1) 지역 산업 비율(종사자 수 기반, SGIS tot_worker)
    //  2) 캠퍼스 전공(학과) 재학생 비율
    //  3) 둘의 차이(GAP)
    // 를 같은 분류(첨단/고기술/… 7개)로 비교하는 것이 핵심입니다.

    private static final List<String> CATEGORY_ORDER = List.of(
            "첨단기술",
            "고기술",
            "중기술",
            "저기술",
            "창의 및 디지털",
            "ICT",
            "전문서비스"
    );

    private static final Logger log = LoggerFactory.getLogger(IndustryAnalysisService.class);

    private final MajorIndustryMappingService majorIndustryMappingService;
    private final SgisCompanyCacheService sgisCompanyCacheService;
    private final CampusStudentQuotaExcelService campusStudentQuotaExcelService;
    private final SgisAdministrativeCodeService sgisAdministrativeCodeService;

    public IndustryAnalysisService(
            MajorIndustryMappingService majorIndustryMappingService,
            SgisCompanyCacheService sgisCompanyCacheService,
            CampusStudentQuotaExcelService campusStudentQuotaExcelService,
            SgisAdministrativeCodeService sgisAdministrativeCodeService
    ) {
        this.majorIndustryMappingService = majorIndustryMappingService;
        this.sgisCompanyCacheService = sgisCompanyCacheService;
        this.campusStudentQuotaExcelService = campusStudentQuotaExcelService;
        this.sgisAdministrativeCodeService = sgisAdministrativeCodeService;
    }

    public IndustryAnalysisResponse analyze(
            String campus,
            String admCd,
            String admNm,
            Integer statsYear
    ) throws IOException {
        String resolvedCampus = normalizeCampus(campus);
        int desiredStatsYear = resolveStatsYear(statsYear);

        String requestedAdmCd = normalizeRequestedAdmCd(admCd);
        String requestedAdmNm = normalizeRequestedAdmNm(admNm);

        SgisAdministrativeCodeService.Resolution sgisResolution =
                sgisAdministrativeCodeService.resolveToSgisAdmCd(requestedAdmCd, requestedAdmNm);
        String resolvedAdmCd = sgisResolution.sgisAdmCd();

        if (!StringUtils.hasText(resolvedAdmCd)) {
            throw new IllegalArgumentException("admCd는 필수입니다.");
        }

        AdmCdResolution admCdResolution = resolveAdmCdAndYear(resolvedAdmCd, desiredStatsYear);
        String usedAdmCd = admCdResolution.usedAdmCd();
        int usedStatsYear = admCdResolution.statsYear();

        boolean admCdFallback = sgisResolution.mappingFallbackApplied() || admCdResolution.dataFallbackApplied();

        // 왜: 행정구역 코드(요청) -> SGIS 코드(변환) -> 실제 사용 코드(데이터 존재 여부로 대체) 흐름을 한 줄로 남기면
        //     "특정 캠퍼스만 0" 같은 문제에서 원인을 바로 좁힐 수 있습니다(민감정보 없음).
        log.info("산업 통계 요청: campus={}, requestedAdmCd={}, requestedAdmNm={}, sgisAdmCd={}, usedAdmCd={}, year={}, fallback={}",
                resolvedCampus, requestedAdmCd, requestedAdmNm, resolvedAdmCd, usedAdmCd, usedStatsYear, admCdFallback);

        Map<String, Long> campusCategoryCounts = countCampusStudentsByCategory(resolvedCampus);
        long campusTotal = campusCategoryCounts.values().stream().mapToLong(Long::longValue).sum();

        Map<String, Long> regionCategoryCounts = countRegionCompaniesByCategory(usedStatsYear, usedAdmCd);
        long regionTotal = regionCategoryCounts.values().stream().mapToLong(Long::longValue).sum();

        List<CategoryRow> rows = new ArrayList<>();
        for (String category : CATEGORY_ORDER) {
            long regionCount = regionCategoryCounts.getOrDefault(category, 0L);
            long campusCount = campusCategoryCounts.getOrDefault(category, 0L);

            double regionRatio = toPercent(regionCount, regionTotal);
            double campusRatio = toPercent(campusCount, campusTotal);
            double gap = campusRatio - regionRatio;

            rows.add(new CategoryRow(category, regionCount, regionRatio, campusCount, campusRatio, gap));
        }

        return new IndustryAnalysisResponse(
                resolvedCampus,
                usedAdmCd,
                requestedAdmCd,
                admCdFallback,
                usedStatsYear,
                campusTotal,
                regionTotal,
                rows
        );
    }

    private Map<String, Long> countCampusStudentsByCategory(String campus) {
        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        for (String category : CATEGORY_ORDER) {
            categoryCounts.put(category, 0L);
        }

        applyCampusCountsFromAdmissionQuotaExcel(campus, categoryCounts);

        return categoryCounts;
    }

    private void applyCampusCountsFromAdmissionQuotaExcel(String campus, Map<String, Long> categoryCounts) {
        // 왜: 사용자가 "현재 엑셀 데이터만으로 통계를 보고 싶다"는 요구가 있어,
        //     DB 재학생 대신 입시율관리.xlsx의 "정원"을 학생수 대체 지표로 사용합니다.
        List<CampusStudentQuotaExcelService.CampusDeptQuota> quotas = campusStudentQuotaExcelService.getCampusDeptQuotas();
        for (CampusStudentQuotaExcelService.CampusDeptQuota row : quotas) {
            if (StringUtils.hasText(campus) && !campus.equals(row.campus())) {
                continue;
            }

            majorIndustryMappingService
                    .findCategoryByCampusAndDept(row.campus(), row.dept())
                    .ifPresent(category -> categoryCounts.compute(
                            category,
                            (k, existing) -> (existing == null ? 0L : existing) + row.quota()
                    ));
        }
    }

    private Map<String, Long> countRegionCompaniesByCategory(int year, String admCd) throws IOException {
        Map<String, List<String>> classCodesByCategory = majorIndustryMappingService.getSgisClassCodesByCategory();

        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        for (String category : CATEGORY_ORDER) {
            categoryCounts.put(category, 0L);
        }

        for (String category : CATEGORY_ORDER) {
            List<String> classCodes = classCodesByCategory.getOrDefault(category, List.of());
            long sum = 0L;
            for (String classCode : classCodes) {
                Long totWorker = sgisCompanyCacheService.getCompanyStats(String.valueOf(year), admCd, classCode).totWorker();
                if (totWorker != null) {
                    sum += totWorker;
                }
            }
            categoryCounts.put(category, sum);
        }

        return categoryCounts;
    }

    private int resolveAvailableStatsYear(int desiredYear, String admCd) throws IOException {
        // 왜: SGIS 사업체 통계는 "최신 연도"가 바로 제공되지 않는 경우가 있어, 사용 가능한 연도로 자동 보정합니다.
        // - 예: 2024가 N/A면 2023으로 내려가서 조회
        for (int y = desiredYear; y >= desiredYear - 5; y--) {
            Map<String, Long> counts = countRegionCompaniesByCategory(y, admCd);
            long total = counts.values().stream().mapToLong(Long::longValue).sum();
            if (total > 0) {
                return y;
            }
        }
        return desiredYear;
    }

    private int resolveStatsYear(Integer statsYear) {
        if (statsYear != null) {
            return statsYear;
        }
        // 왜: SGIS는 당해 연도(예: 2026)는 아직 미제공일 가능성이 높아서 기본값을 전년도(-1)로 둡니다.
        return Year.now().getValue() - 1;
    }

    private String normalizeCampus(String campus) {
        if (!StringUtils.hasText(campus) || "전체".equals(campus) || "전체 캠퍼스".equals(campus)) {
            return null;
        }
        return campus.trim();
    }

    private String normalizeRequestedAdmCd(String admCd) {
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

    private AdmCdResolution resolveAdmCdAndYear(String resolvedAdmCd, int desiredYear) throws IOException {
        // 왜: 특정 행정구역 코드가 외부 API에 존재하지 않으면 result가 비어 0으로만 계산될 수 있습니다.
        //      화면이 전부 0으로 깨져 보이지 않도록, "한 단계 상위 행정구역"으로 순차 대체해 데이터가 존재하는 코드를 찾습니다.
        List<String> candidates = buildAdmCdFallbackCandidates(resolvedAdmCd);

        for (String candidateAdmCd : candidates) {
            int candidateYear = resolveAvailableStatsYear(desiredYear, candidateAdmCd);
            Map<String, Long> counts = countRegionCompaniesByCategory(candidateYear, candidateAdmCd);
            long total = counts.values().stream().mapToLong(Long::longValue).sum();
            if (total > 0) {
                boolean dataFallbackApplied = !candidateAdmCd.equals(resolvedAdmCd);
                if (dataFallbackApplied) {
                    log.info("산업 통계 행정구역 데이터 대체: resolvedAdmCd={}, usedAdmCd={}, year={}", resolvedAdmCd, candidateAdmCd, candidateYear);
                }
                return new AdmCdResolution(resolvedAdmCd, candidateAdmCd, dataFallbackApplied, candidateYear);
            }
        }

        // 여기까지 왔다면(전국까지) 데이터가 없다는 뜻이라, 원인 파악을 위해 경고 로그를 남깁니다.
        log.warn("산업 통계 행정구역 데이터 없음(대체 실패): resolvedAdmCd={}, desiredYear={}", resolvedAdmCd, desiredYear);
        return new AdmCdResolution(resolvedAdmCd, resolvedAdmCd, false, desiredYear);
    }

    private List<String> buildAdmCdFallbackCandidates(String admCd) {
        // 왜: 7자리(읍면동) → 5자리(시군구) → 2자리(시도) 순서로 대체합니다.
        Set<String> candidates = new LinkedHashSet<>();

        String current = StringUtils.hasText(admCd) ? admCd.trim() : null;
        if (!StringUtils.hasText(current)) {
            return List.of();
        }

        candidates.add(current);

        if (current.matches("\\d+")) {
            while (current.length() > 2) {
                if (current.length() > 5) {
                    current = current.substring(0, 5);
                } else {
                    current = current.substring(0, 2);
                }
                candidates.add(current);
            }
        }

        return List.copyOf(candidates);
    }

    private double toPercent(long part, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return (part * 100.0) / total;
    }

    public record IndustryAnalysisResponse(
            String campus,
            String admCd,
            String requestedAdmCd,
            boolean admCdFallback,
            int statsYear,
            long campusStudentTotal,
            long regionCompanyTotal,
            List<CategoryRow> rows
    ) {
    }

    public record CategoryRow(
            String category,
            long regionCount,
            double regionRatio,
            long campusCount,
            double campusRatio,
            double gap
    ) {
    }

    private record AdmCdResolution(
            String resolvedAdmCd,
            String usedAdmCd,
            boolean dataFallbackApplied,
            int statsYear
    ) {
    }
}
