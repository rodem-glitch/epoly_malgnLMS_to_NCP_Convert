package kr.polytech.lms.statistics.internalstats;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kr.polytech.lms.statistics.util.StatisticsFileLocator;

@Service
public class InternalStatisticsService {
    // 왜: 내부(입학/취업) 통계는 실제 운영에서는 DB 적재가 정석이지만,
    //     초기 고도화 단계에서는 "통계 폴더의 엑셀"을 그대로 읽어 빠르게 화면을 구현할 수 있습니다.

    private final StatisticsDataProperties properties;
    private final DataFormatter formatter;

    private static final Pattern YEAR_PATTERN = Pattern.compile("(?<!\\d)(20\\d{2})(?!\\d)");

    private volatile List<EmploymentRow> cachedEmploymentRows;
    private final Object employmentLock = new Object();

    private volatile List<AdmissionRow> cachedAdmissionRows;
    private final Object admissionLock = new Object();

    private final Map<Path, EmploymentFileCache> employmentFileCache = new ConcurrentHashMap<>();

    public InternalStatisticsService(StatisticsDataProperties properties) {
        this.properties = properties;
        this.formatter = new DataFormatter(Locale.KOREA);
    }

    public List<Integer> getAvailableEmploymentYears() {
        // 왜: LLM이 "가능한 연도"를 알고 계획을 세우면, 불필요한 되물음이 줄어듭니다.
        try {
            Path base = resolveEmploymentFilePath();
            return indexEmploymentFiles(base).keySet().stream().sorted().toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<EmploymentStat> getEmploymentStatsForYear(int year) {
        Optional<Path> file = resolveEmploymentFilePathForYear(year);
        if (file.isEmpty()) {
            return List.of();
        }

        List<EmploymentRow> rows = getOrLoadEmploymentRows(file.get());
        return rows.stream()
                .map(r -> new EmploymentStat(r.campus(), r.dept(), r.employmentRate()))
                .toList();
    }

    public EmploymentRawResult getEmploymentRawData(String campus, Integer year) {
        // 왜: 로우 데이터 탭은 차트용 집계가 아니라, 엑셀의 행을 가능한 그대로 내려주기 위해 별도 메서드를 둡니다.
        Path path = resolveEmploymentPathForRawData(year);
        Integer usedYear = resolveYearFromPath(path);

        List<EmploymentRow> rows = getOrLoadEmploymentRows(path);
        List<EmploymentRow> filtered = filterEmploymentRows(rows, campus);
        return new EmploymentRawResult(usedYear, filtered);
    }

    public Optional<Path> resolveEmploymentFilePathForYear(int year) {
        Path base = resolveEmploymentFilePath();
        return Optional.ofNullable(indexEmploymentFiles(base).get(year));
    }

    public List<DepartmentRate> getEmploymentRates(String campus) {
        String resolvedCampus = normalizeCampus(campus);

        // 왜: 화면 기본값이 "전체 캠퍼스"라서, campus가 비어있을 수 있습니다.
        //     이 경우에는 (학과명 기준) 취업자수/대상자수를 합산해 전체 취업률을 계산합니다.
        if (!StringUtils.hasText(resolvedCampus)) {
            Map<String, double[]> sumsByDept = new LinkedHashMap<>();
            for (EmploymentRow r : getOrLoadEmploymentRows()) {
                if (!StringUtils.hasText(r.dept()) || r.employed() == null || r.employTarget() == null) {
                    continue;
                }
                double[] sums = sumsByDept.computeIfAbsent(r.dept(), k -> new double[]{0.0, 0.0});
                sums[0] += r.employed();
                sums[1] += r.employTarget();
            }

            return sumsByDept.entrySet().stream()
                    .filter(e -> e.getValue()[1] > 0)
                    .map(e -> new DepartmentRate(e.getKey(), (e.getValue()[0] / e.getValue()[1]) * 100.0))
                    .sorted(Comparator.comparingDouble(DepartmentRate::rate).reversed())
                    .toList();
        }

        return getOrLoadEmploymentRows().stream()
                .filter(r -> resolvedCampus.equals(r.campus()))
                .map(r -> new DepartmentRate(r.dept(), r.employmentRate()))
                .sorted(Comparator.comparingDouble(DepartmentRate::rate).reversed())
                .toList();
    }

    public List<DepartmentRate> getTopEmploymentRates(String campus, int top) {
        return getEmploymentRates(campus).stream()
                .limit(Math.max(1, top))
                .toList();
    }

    public List<DepartmentRate> getAdmissionFillRates(String campus) {
        String resolvedCampus = normalizeCampus(campus);

        // 왜: 화면 기본값이 "전체 캠퍼스"라서, campus가 비어있을 수 있습니다.
        //     이 경우에는 (학과명 기준) usedCount/정원을 합산해 전체 충원률을 계산합니다.
        if (!StringUtils.hasText(resolvedCampus)) {
            Map<String, double[]> sumsByDept = new LinkedHashMap<>();
            for (AdmissionRow r : getOrLoadAdmissionRows()) {
                if (!StringUtils.hasText(r.dept()) || r.quota() == null || r.usedCount() == null) {
                    continue;
                }
                double[] sums = sumsByDept.computeIfAbsent(r.dept(), k -> new double[]{0.0, 0.0});
                sums[0] += r.usedCount();
                sums[1] += r.quota();
            }

            return sumsByDept.entrySet().stream()
                    .filter(e -> e.getValue()[1] > 0)
                    .map(e -> {
                        double rate = (e.getValue()[0] / e.getValue()[1]) * 100.0;
                        // 왜: 지원(접수) 기반 비율은 100%를 초과할 수 있어, 화면/기존 로직과 동일하게 100 상한을 둡니다.
                        rate = Math.min(rate, 100.0);
                        return new DepartmentRate(e.getKey(), rate);
                    })
                    .sorted(Comparator.comparingDouble(DepartmentRate::rate).reversed())
                    .toList();
        }

        return getOrLoadAdmissionRows().stream()
                .filter(r -> resolvedCampus.equals(r.campus()))
                .map(r -> new DepartmentRate(r.dept(), r.fillRate()))
                .sorted(Comparator.comparingDouble(DepartmentRate::rate).reversed())
                .toList();
    }

    public List<DepartmentRate> getTopAdmissionFillRates(String campus, int top) {
        return getAdmissionFillRates(campus).stream()
                .limit(Math.max(1, top))
                .toList();
    }

    public AdmissionRawResult getAdmissionRawData(String campus) {
        // 왜: 입학 로우 데이터는 현재 단일 엑셀에서 읽기 때문에, 캠퍼스 필터만 적용해 제공합니다.
        Integer usedYear = resolveAdmissionFileYear();
        List<AdmissionRow> rows = getOrLoadAdmissionRows();
        List<AdmissionRow> filtered = filterAdmissionRows(rows, campus);
        return new AdmissionRawResult(usedYear, filtered);
    }

    public List<Integer> getAvailableAdmissionYears() {
        Integer year = resolveAdmissionFileYear();
        if (year == null) {
            return List.of();
        }
        return List.of(year);
    }

    private List<EmploymentRow> getOrLoadEmploymentRows() {
        List<EmploymentRow> existing = cachedEmploymentRows;
        if (existing != null) {
            return existing;
        }

        synchronized (employmentLock) {
            if (cachedEmploymentRows != null) {
                return cachedEmploymentRows;
            }
            cachedEmploymentRows = loadEmploymentRowsFromExcel(resolveEmploymentFilePath());
            return cachedEmploymentRows;
        }
    }

    private List<EmploymentRow> getOrLoadEmploymentRows(Path path) {
        long lastModified = safeLastModifiedMillis(path);
        EmploymentFileCache cached = employmentFileCache.get(path);
        if (cached != null && cached.lastModifiedMillis() == lastModified) {
            return cached.rows();
        }

        List<EmploymentRow> rows = loadEmploymentRowsFromExcel(path);
        employmentFileCache.put(path, new EmploymentFileCache(lastModified, rows));
        return rows;
    }

    private List<AdmissionRow> getOrLoadAdmissionRows() {
        List<AdmissionRow> existing = cachedAdmissionRows;
        if (existing != null) {
            return existing;
        }

        synchronized (admissionLock) {
            if (cachedAdmissionRows != null) {
                return cachedAdmissionRows;
            }
            cachedAdmissionRows = loadAdmissionRowsFromExcel();
            return cachedAdmissionRows;
        }
    }

    private List<EmploymentRow> loadEmploymentRowsFromExcel(Path path) {
        try (InputStream in = Files.newInputStream(path); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = Objects.requireNonNullElse(workbook.getSheet("학과별취업현황(종합)"), workbook.getSheetAt(0));

            List<EmploymentRow> rows = new ArrayList<>();
            for (Row row : sheet) {
                // 왜: 상단 제목/헤더 영역을 건너뛰고, 실제 데이터 행만 처리합니다.
                if (row.getRowNum() < 8) {
                    continue;
                }

                String campus = getCellString(row, 1);
                String dept = getCellString(row, 3);

                Double employed = getCellNumber(row, 13);
                Double employTarget = getCellNumber(row, 12);

                if (!StringUtils.hasText(campus) || !StringUtils.hasText(dept)) {
                    continue;
                }
                if (isSummaryDept(dept)) {
                    continue;
                }
                if (employed == null || employTarget == null || employTarget <= 0) {
                    continue;
                }

                double rate = (employed / employTarget) * 100.0;
                rows.add(new EmploymentRow(normalizeCampus(campus), dept.trim(), employed, employTarget, rate));
            }

            return rows;
        } catch (Exception e) {
            throw new IllegalStateException("취업 통계 엑셀을 읽지 못했습니다. statistics.data.employment-file 경로를 확인해 주세요.", e);
        }
    }

    private Path resolveEmploymentFilePath() {
        return resolveFilePath(properties.getEmploymentFile(), "statistics.data.employment-file");
    }

    private Path resolveEmploymentPathForRawData(Integer year) {
        // 왜: 로우 데이터 탭에서 연도를 선택하면 해당 연도 파일을 우선 사용합니다.
        if (year != null) {
            Optional<Path> byYear = resolveEmploymentFilePathForYear(year);
            if (byYear.isEmpty()) {
                throw new IllegalArgumentException("해당 연도의 취업률 엑셀 파일을 찾을 수 없습니다. year=" + year);
            }
            return byYear.get();
        }
        return resolveEmploymentFilePath();
    }

    private Integer resolveYearFromPath(Path path) {
        if (path == null || path.getFileName() == null) return null;
        return extractYear(path.getFileName().toString());
    }

    private Map<Integer, Path> indexEmploymentFiles(Path baseFile) {
        // 왜: 운영에서는 취업률 파일이 연도별로 쌓이는 경우가 많아서, 파일명에 들어있는 연도(20xx)를 기준으로 자동 매칭합니다.
        Map<Integer, Path> byYear = new LinkedHashMap<>();

        addEmploymentFileCandidate(byYear, baseFile);

        Path dir = baseFile.getParent();
        if (dir == null || !Files.isDirectory(dir)) {
            return byYear;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.xlsx")) {
            for (Path p : stream) {
                addEmploymentFileCandidate(byYear, p);
            }
        } catch (Exception ignored) {
            // 왜: 폴더 접근이 실패해도, 최소한 baseFile로는 동작하게 둡니다.
        }

        return byYear;
    }

    private void addEmploymentFileCandidate(Map<Integer, Path> byYear, Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString();
        Integer year = extractYear(name);
        if (year == null) {
            return;
        }

        // 왜: 같은 연도 파일이 여러 개면(수정본/재배포본 등) 마지막 수정 시간이 더 최신인 걸 우선합니다.
        Path existing = byYear.get(year);
        if (existing == null) {
            byYear.put(year, file);
            return;
        }

        long existingLm = safeLastModifiedMillis(existing);
        long candidateLm = safeLastModifiedMillis(file);
        if (candidateLm >= existingLm) {
            byYear.put(year, file);
        }
    }

    private Integer extractYear(String text) {
        if (!StringUtils.hasText(text)) return null;
        Matcher m = YEAR_PATTERN.matcher(text);
        if (!m.find()) return null;
        try {
            return Integer.parseInt(m.group(1));
        } catch (Exception e) {
            return null;
        }
    }

    private long safeLastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }

    private List<AdmissionRow> loadAdmissionRowsFromExcel() {
        Path path = resolveAdmissionFilePath();

        try (InputStream in = Files.newInputStream(path); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (workbook.getSheet("2025.11.25. 24시") != null) {
                // 왜: 현재 파일은 '2025.11.25. 24시' 시트에 학과별 정원/모집인원 정보가 들어 있습니다.
                sheet = workbook.getSheet("2025.11.25. 24시");
            }

            List<AdmissionRow> rows = new ArrayList<>();
            for (Row row : sheet) {
                if (row.getRowNum() < 8) {
                    continue;
                }

                String campus = getCellString(row, 1);
                String dept = getCellString(row, 3);

                Double quota = getCellNumber(row, 5);
                Double recruit = getCellNumber(row, 7);

                // 왜: '입시율관리.xlsx'는 파일 버전에 따라 "지원현황/등록현황" 컬럼이 뒤쪽에 추가되어 있습니다.
                //     (예: 2025.11.25. 24시 시트 기준)
                //     - 지원현황 합계(65열)
                //     - 등록현황 합계(71열)
                //     화면의 "입학충원률"은 정식으로는 등록현황 기반이 맞지만,
                //     등록이 아직 집계되지 않은 시점에는 지원현황으로라도 "충원 가능성"을 비교할 수 있게 합니다.
                Double applicants = getCellNumber(row, 64);  // 65열(1-based) = 64(0-based)
                Double registered = getCellNumber(row, 70);  // 71열(1-based) = 70(0-based)
                AdmissionBasis basis = resolveAdmissionBasis(registered, applicants, recruit);
                Double usedCount = resolveAdmissionUsedCount(basis, registered, applicants, recruit);

                if (!StringUtils.hasText(campus) || !StringUtils.hasText(dept)) {
                    continue;
                }
                if (isSummaryDept(dept)) {
                    continue;
                }
                if (quota == null || quota <= 0 || usedCount == null) {
                    continue;
                }

                double rate = (usedCount / quota) * 100.0;
                if (basis != AdmissionBasis.REGISTERED) {
                    // 왜: 지원(접수) 기반 비율은 100%를 초과할 수 있으므로,
                    //     "충원률" 용어와 혼동되지 않도록 100으로 상한을 둡니다.
                    rate = Math.min(rate, 100.0);
                }
                rows.add(new AdmissionRow(
                        normalizeCampus(campus),
                        dept.trim(),
                        quota,
                        recruit,
                        applicants,
                        registered,
                        basis,
                        usedCount,
                        rate
                ));
            }

            return rows;
        } catch (Exception e) {
            throw new IllegalStateException("입시 통계 엑셀을 읽지 못했습니다. statistics.data.admission-file 경로를 확인해 주세요.", e);
        }
    }

    private Path resolveAdmissionFilePath() {
        return resolveFilePath(properties.getAdmissionFile(), "statistics.data.admission-file");
    }

    private Integer resolveAdmissionFileYear() {
        return resolveYearFromPath(resolveAdmissionFilePath());
    }

    private Path resolveFilePath(String filePath, String configKeyName) {
        if (!StringUtils.hasText(filePath)) {
            throw new IllegalStateException("통계 파일 설정이 없습니다. " + configKeyName + " 을 설정해 주세요.");
        }

        Path configured = Path.of(filePath);
        return StatisticsFileLocator.tryResolve(filePath)
                .orElseThrow(() -> new IllegalStateException(
                        "통계 파일을 찾을 수 없습니다. 경로=" + configured + " (현재작업폴더=" + Path.of("").toAbsolutePath() + ")"
                ));
    }

    private String requireCampus(String campus) {
        String resolvedCampus = normalizeCampus(campus);
        if (!StringUtils.hasText(resolvedCampus)) {
            throw new IllegalArgumentException("campus는 필수입니다.");
        }
        return resolvedCampus;
    }

    private String normalizeCampus(String campus) {
        String v = campus == null ? "" : campus.trim();
        if (!StringUtils.hasText(v) || "전체".equals(v) || "전체 캠퍼스".equals(v)) {
            return null;
        }
        if (v.endsWith("캠퍼스")) {
            return v.substring(0, v.length() - "캠퍼스".length()).trim();
        }
        return v;
    }

    private List<EmploymentRow> filterEmploymentRows(List<EmploymentRow> rows, String campus) {
        String resolvedCampus = normalizeCampus(campus);
        if (!StringUtils.hasText(resolvedCampus)) {
            return rows;
        }
        return rows.stream()
                .filter(r -> resolvedCampus.equals(r.campus()))
                .toList();
    }

    private List<AdmissionRow> filterAdmissionRows(List<AdmissionRow> rows, String campus) {
        String resolvedCampus = normalizeCampus(campus);
        if (!StringUtils.hasText(resolvedCampus)) {
            return rows;
        }
        return rows.stream()
                .filter(r -> resolvedCampus.equals(r.campus()))
                .toList();
    }

    private AdmissionBasis resolveAdmissionBasis(Double registered, Double applicants, Double recruit) {
        if (registered != null && registered > 0) {
            return AdmissionBasis.REGISTERED;
        }
        if (applicants != null && applicants >= 0) {
            return AdmissionBasis.APPLICANTS;
        }
        if (recruit != null && recruit >= 0) {
            return AdmissionBasis.RECRUIT;
        }
        return AdmissionBasis.UNKNOWN;
    }

    private Double resolveAdmissionUsedCount(AdmissionBasis basis, Double registered, Double applicants, Double recruit) {
        return switch (basis) {
            case REGISTERED -> registered;
            case APPLICANTS -> applicants;
            case RECRUIT -> recruit;
            case UNKNOWN -> null;
        };
    }

    private boolean isSummaryDept(String dept) {
        String v = dept.trim();
        return v.equals("소계") || v.equals("총계") || v.endsWith("소계") || v.contains("별 소계");
    }

    private String getCellString(Row row, int cellIndexZeroBased) {
        Cell cell = row.getCell(cellIndexZeroBased);
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Double getCellNumber(Row row, int cellIndexZeroBased) {
        String value = getCellString(row, cellIndexZeroBased);
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = value.replace(",", "");
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record EmploymentRow(String campus, String dept, Double employed, Double employTarget, double employmentRate) {
    }

    public record EmploymentStat(String campus, String dept, double employmentRate) {
    }

    public record AdmissionRow(
            String campus,
            String dept,
            Double quota,
            Double recruit,
            Double applicants,
            Double registered,
            AdmissionBasis basis,
            Double usedCount,
            double fillRate
    ) {
    }

    public record DepartmentRate(String dept, double rate) {
    }

    public record EmploymentRawResult(Integer year, List<EmploymentRow> rows) {
    }

    public record AdmissionRawResult(Integer year, List<AdmissionRow> rows) {
    }

    private record EmploymentFileCache(long lastModifiedMillis, List<EmploymentRow> rows) {
        EmploymentFileCache(long lastModifiedMillis, List<EmploymentRow> rows) {
            this.lastModifiedMillis = lastModifiedMillis;
            this.rows = (rows == null) ? List.of() : List.copyOf(rows);
        }
    }

    public enum AdmissionBasis {
        REGISTERED,
        APPLICANTS,
        RECRUIT,
        UNKNOWN
    }
}
