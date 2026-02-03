package kr.polytech.lms.statistics.dashboard.controller;

import kr.polytech.lms.statistics.dashboard.service.IndustryAnalysisService;
import kr.polytech.lms.statistics.dashboard.service.PopulationComparisonService;
import kr.polytech.lms.statistics.dashboard.service.StatisticsExcelExportService;
import kr.polytech.lms.statistics.dashboard.service.StatisticsMetaService;
import kr.polytech.lms.statistics.internalstats.InternalStatisticsService;
import kr.polytech.lms.statistics.mapping.MajorIndustryMappingService;
import kr.polytech.lms.statistics.student.excel.CampusStudentPopulationExcelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/statistics/api")
public class StatisticsDashboardApiController {
    // 왜: 통계 화면(정적 HTML)이 필요한 데이터만 API로 받아서 렌더링할 수 있도록 JSON API를 제공합니다.

    private static final Logger log = LoggerFactory.getLogger(StatisticsDashboardApiController.class);

    private final StatisticsMetaService statisticsMetaService;
    private final IndustryAnalysisService industryAnalysisService;
    private final PopulationComparisonService populationComparisonService;
    private final InternalStatisticsService internalStatisticsService;
    private final StatisticsExcelExportService statisticsExcelExportService;
    private final CampusStudentPopulationExcelService campusStudentPopulationExcelService;

    public StatisticsDashboardApiController(
            StatisticsMetaService statisticsMetaService,
            IndustryAnalysisService industryAnalysisService,
            PopulationComparisonService populationComparisonService,
            InternalStatisticsService internalStatisticsService,
            StatisticsExcelExportService statisticsExcelExportService,
            CampusStudentPopulationExcelService campusStudentPopulationExcelService
    ) {
        this.statisticsMetaService = statisticsMetaService;
        this.industryAnalysisService = industryAnalysisService;
        this.populationComparisonService = populationComparisonService;
        this.internalStatisticsService = internalStatisticsService;
        this.statisticsExcelExportService = statisticsExcelExportService;
        this.campusStudentPopulationExcelService = campusStudentPopulationExcelService;
    }

    @GetMapping("/meta/campus-groups")
    public List<MajorIndustryMappingService.CampusGroup> campusGroups() {
        return statisticsMetaService.getCampusGroups();
    }

    @GetMapping("/industry/analysis")
    public ResponseEntity<?> industryAnalysis(
            @RequestParam(name = "campus", required = false) String campus,
            @RequestParam(name = "admCd", required = false) String admCd,
            @RequestParam(name = "admNm", required = false) String admNm,
            @RequestParam(name = "statsYear", required = false) Integer statsYear
    ) throws IOException {
        try {
            // 왜: 실제 호출 파라미터를 로그로 남겨야 "특정 캠퍼스만 0" 같은 문제를 빠르게 재현/분석할 수 있습니다.
            log.info("통계 API 호출(산업): campus={}, admCd={}, admNm={}, statsYear={}", campus, admCd, admNm, statsYear);
            return ResponseEntity.ok(industryAnalysisService.analyze(campus, admCd, admNm, statsYear));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(e.getMessage()));
        }
    }

    @GetMapping("/population/compare")
    public ResponseEntity<?> populationCompare(
            @RequestParam(name = "campus") String campus,
            @RequestParam(name = "admCd", required = false) String admCd,
            @RequestParam(name = "admNm", required = false) String admNm,
            @RequestParam(name = "populationYear", required = false) Integer populationYear
    ) throws IOException {
        try {
            // 왜: 실제 호출 파라미터를 로그로 남겨야 "특정 캠퍼스만 0" 같은 문제를 빠르게 재현/분석할 수 있습니다.
            log.info("통계 API 호출(인구): campus={}, admCd={}, admNm={}, populationYear={}", campus, admCd, admNm, populationYear);
            return ResponseEntity.ok(populationComparisonService.compare(campus, admCd, admNm, populationYear));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(e.getMessage()));
        }
    }

    @GetMapping("/internal/employment/top")
    public ResponseEntity<?> topEmploymentRates(
            @RequestParam(name = "campus") String campus,
            @RequestParam(name = "top", defaultValue = "10") int top
    ) {
        try {
            return ResponseEntity.ok(internalStatisticsService.getTopEmploymentRates(campus, top));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(e.getMessage()));
        }
    }

    @GetMapping("/internal/admission/top")
    public ResponseEntity<?> topAdmissionFillRates(
            @RequestParam(name = "campus") String campus,
            @RequestParam(name = "top", defaultValue = "10") int top
    ) {
        try {
            return ResponseEntity.ok(internalStatisticsService.getTopAdmissionFillRates(campus, top));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(e.getMessage()));
        }
    }

    @GetMapping("/internal/export")
    public ResponseEntity<?> exportInternalExcel(
            @RequestParam(name = "campus") String campus
    ) {
        try {
            byte[] bytes = statisticsExcelExportService.exportInternal(
                    campus,
                    internalStatisticsService.getEmploymentRates(campus),
                    internalStatisticsService.getAdmissionFillRates(campus)
            );
            return toExcelResponse("내부통계_" + campus + ".xlsx", bytes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(e.getMessage()));
        }
    }

    @GetMapping("/population/export")
    public ResponseEntity<?> exportPopulationExcel(
            @RequestParam(name = "campus") String campus,
            @RequestParam(name = "admCd", required = false) String admCd,
            @RequestParam(name = "admNm", required = false) String admNm,
            @RequestParam(name = "populationYear", required = false) Integer populationYear
    ) throws IOException {
        try {
            PopulationComparisonService.PopulationComparisonResponse response =
                    populationComparisonService.compare(campus, admCd, admNm, populationYear);
            byte[] bytes = statisticsExcelExportService.exportPopulation(response);
            String filename = "인구비율_%s_%s_%s.xlsx".formatted(campus, response.admCd(), response.populationYear());
            return toExcelResponse(filename, bytes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(e.getMessage()));
        }
    }

    @GetMapping("/industry/export")
    public ResponseEntity<?> exportIndustryExcel(
            @RequestParam(name = "campus", required = false) String campus,
            @RequestParam(name = "admCd", required = false) String admCd,
            @RequestParam(name = "admNm", required = false) String admNm,
            @RequestParam(name = "statsYear", required = false) Integer statsYear
    ) throws IOException {
        try {
            IndustryAnalysisService.IndustryAnalysisResponse response =
                    industryAnalysisService.analyze(campus, admCd, admNm, statsYear);
            byte[] bytes = statisticsExcelExportService.exportIndustry(response);
            String filename = "산업비율_%s_%s_%s.xlsx".formatted(
                    response.campus() == null ? "전체" : response.campus(),
                    response.admCd(),
                    response.statsYear()
            );
            return toExcelResponse(filename, bytes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(e.getMessage()));
        }
    }

    @GetMapping("/rawdata")
    public ResponseEntity<?> rawData(
            @RequestParam(name = "category") String category,
            @RequestParam(name = "campus", required = false) String campus,
            @RequestParam(name = "year", required = false) Integer year
    ) {
        try {
            return ResponseEntity.ok(buildRawDataResponse(category, campus, year));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(e.getMessage()));
        }
    }

    @GetMapping("/rawdata/export")
    public ResponseEntity<?> exportRawDataExcel(
            @RequestParam(name = "category") String category,
            @RequestParam(name = "campus", required = false) String campus,
            @RequestParam(name = "year", required = false) Integer year
    ) {
        try {
            RawDataResponse response = buildRawDataResponse(category, campus, year);
            String campusLabel = (campus == null || campus.isBlank()) ? "전체" : campus.trim();
            String filename = "로우데이터_%s_%s.xlsx".formatted(
                    response.categoryLabel(),
                    "%s_%s".formatted(campusLabel, response.year() == null ? "전체" : response.year())
            );
            byte[] bytes = statisticsExcelExportService.exportRawData("로우데이터", response.columns(), response.rows());
            return toExcelResponse(filename, bytes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(e.getMessage()));
        }
    }

    private RawDataResponse buildRawDataResponse(String category, String campus, Integer year) {
        RawDataCategory rawCategory = RawDataCategory.from(category);
        return switch (rawCategory) {
            case EMPLOYMENT -> buildEmploymentRawData(rawCategory, campus, year);
            case ADMISSION -> buildAdmissionRawData(rawCategory, campus, year);
            case POPULATION -> buildPopulationRawData(rawCategory, campus, year);
        };
    }

    private RawDataResponse buildEmploymentRawData(RawDataCategory rawCategory, String campus, Integer year) {
        InternalStatisticsService.EmploymentRawResult result = internalStatisticsService.getEmploymentRawData(campus, year);

        List<List<Object>> rows = new ArrayList<>();
        for (InternalStatisticsService.EmploymentRow r : result.rows()) {
            List<Object> row = new ArrayList<>();
            row.add(r.campus());
            row.add(r.dept());
            row.add(r.employed());
            row.add(r.employTarget());
            row.add(r.employmentRate());
            rows.add(row);
        }

        List<String> columns = List.of("캠퍼스", "학과", "취업자수", "취업대상자수", "취업률(%)");
        List<Integer> availableYears = internalStatisticsService.getAvailableEmploymentYears();
        return new RawDataResponse(rawCategory.key, rawCategory.label, result.year(), availableYears, columns, rows);
    }

    private RawDataResponse buildAdmissionRawData(RawDataCategory rawCategory, String campus, Integer year) {
        List<Integer> availableYears = internalStatisticsService.getAvailableAdmissionYears();
        if (year != null && !availableYears.isEmpty() && !availableYears.contains(year)) {
            throw new IllegalArgumentException("해당 연도의 입학 엑셀 파일을 찾을 수 없습니다. year=" + year);
        }

        InternalStatisticsService.AdmissionRawResult result = internalStatisticsService.getAdmissionRawData(campus);
        Integer usedYear = year != null ? year : result.year();

        List<List<Object>> rows = new ArrayList<>();
        for (InternalStatisticsService.AdmissionRow r : result.rows()) {
            List<Object> row = new ArrayList<>();
            row.add(r.campus());
            row.add(r.dept());
            row.add(r.quota());
            row.add(r.recruit());
            row.add(r.applicants());
            row.add(r.registered());
            row.add(resolveAdmissionBasisLabel(r.basis()));
            row.add(r.usedCount());
            row.add(r.fillRate());
            rows.add(row);
        }

        List<String> columns = List.of(
                "캠퍼스",
                "학과",
                "정원",
                "모집인원",
                "지원자수",
                "등록자수",
                "산출기준",
                "기준값",
                "입학충원률(%)"
        );
        return new RawDataResponse(rawCategory.key, rawCategory.label, usedYear, availableYears, columns, rows);
    }

    private RawDataResponse buildPopulationRawData(RawDataCategory rawCategory, String campus, Integer year) {
        String yearText = year == null ? null : String.valueOf(year);
        CampusStudentPopulationExcelService.PopulationRawData data =
                campusStudentPopulationExcelService.getRawData(campus, yearText, null);

        List<List<Object>> rows = new ArrayList<>();
        List<String> columns;

        if (data.type() == CampusStudentPopulationExcelService.PopulationRawType.AGGREGATED) {
            columns = List.of("캠퍼스", "연도", "학기", "연령대", "남성", "여성", "합계");
            for (CampusStudentPopulationExcelService.PopulationRawRow r : data.rows()) {
                long male = r.male() == null ? 0L : r.male();
                long female = r.female() == null ? 0L : r.female();
                List<Object> row = new ArrayList<>();
                row.add(r.campus());
                row.add(r.year());
                row.add(r.term());
                row.add(r.ageBand());
                row.add(r.male());
                row.add(r.female());
                row.add(male + female);
                rows.add(row);
            }
        } else {
            columns = List.of("캠퍼스", "연도", "학기", "출생연도", "성별");
            for (CampusStudentPopulationExcelService.PopulationRawRow r : data.rows()) {
                List<Object> row = new ArrayList<>();
                row.add(r.campus());
                row.add(r.year());
                row.add(r.term());
                row.add(r.birthYear());
                row.add(r.gender());
                rows.add(row);
            }
        }

        return new RawDataResponse(rawCategory.key, rawCategory.label, year, data.availableYears(), columns, rows);
    }

    private String resolveAdmissionBasisLabel(InternalStatisticsService.AdmissionBasis basis) {
        if (basis == null) return "미정";
        return switch (basis) {
            case REGISTERED -> "등록";
            case APPLICANTS -> "지원";
            case RECRUIT -> "모집";
            case UNKNOWN -> "미정";
        };
    }

    private ResponseEntity<byte[]> toExcelResponse(String filename, byte[] bytes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    private record ApiError(String message) {
    }

    private record RawDataResponse(
            String category,
            String categoryLabel,
            Integer year,
            List<Integer> availableYears,
            List<String> columns,
            List<List<Object>> rows
    ) {
    }

    private enum RawDataCategory {
        EMPLOYMENT("employment", "취업"),
        ADMISSION("admission", "입학"),
        POPULATION("population", "재학생인구");

        private final String key;
        private final String label;

        RawDataCategory(String key, String label) {
            this.key = key;
            this.label = label;
        }

        private static RawDataCategory from(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("category는 필수입니다.");
            }
            for (RawDataCategory c : values()) {
                if (c.key.equalsIgnoreCase(value.trim())) {
                    return c;
                }
            }
            throw new IllegalArgumentException("지원하지 않는 category입니다. (employment/admission/population)");
        }
    }
}
