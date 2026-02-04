package kr.polytech.lms.statistics.student.excel;

import kr.polytech.lms.statistics.internalstats.StatisticsDataProperties;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import kr.polytech.lms.statistics.util.StatisticsFileLocator;

@Service
public class CampusStudentPopulationExcelService {
    // 왜: "캠퍼스 학생비율(연령대/성별)"을 DB가 아니라 엑셀 기반으로 계산해야 하는 경우가 있습니다.
    // - 예) 운영 DB에는 생년월일/성별이 없거나, 별도 집계 파일(엑셀)로만 관리하는 경우
    // 이 서비스는 엑셀 파일에서 캠퍼스별 연령대/성별 분포를 읽어, 화면에서 비교할 수 있는 형태로 제공합니다.

    private final StatisticsDataProperties properties;
    private final DataFormatter formatter;

    private volatile Cache cache;
    private final Object cacheLock = new Object();

    public CampusStudentPopulationExcelService(StatisticsDataProperties properties) {
        this.properties = properties;
        this.formatter = new DataFormatter(Locale.KOREA);
    }

    public boolean isEnabled() {
        String file = properties.getStudentPopulationFile();
        if (!StringUtils.hasText(file)) {
            return findDefaultFile().isPresent();
        }
        return StatisticsFileLocator.tryResolve(file).isPresent();
    }

    public Map<String, GenderCount> countByAgeBandAndGender(
            String campus,
            String year,
            String term,
            int baseYear
    ) {
        String resolvedCampus = normalizeCampus(campus);
        Cache loaded = getOrLoadCache();
        return loaded.countByAgeBandAndGender(resolvedCampus, year, term, baseYear);
    }

    public PopulationRawData getRawData(String campus, String year, String term) {
        Cache loaded = getOrLoadCache();
        String resolvedCampus = normalizeCampus(campus);
        String resolvedYear = normalizeSimple(year);
        String resolvedTerm = normalizeSimple(term);

        // 왜: 엑셀 구조가 (학생 로스터) vs (연령대 집계) 두 형태라서, 실제로 있는 형태를 그대로 내려줍니다.
        if (!loaded.aggregates.isEmpty()) {
            List<PopulationRawRow> rows = new ArrayList<>();
            for (AgeAggregateRow row : loaded.aggregates()) {
                if (StringUtils.hasText(resolvedCampus) && !resolvedCampus.equals(row.campus())) {
                    continue;
                }
                if (StringUtils.hasText(resolvedYear) && StringUtils.hasText(row.year()) && !resolvedYear.equals(row.year())) {
                    continue;
                }
                if (StringUtils.hasText(resolvedTerm) && StringUtils.hasText(row.term()) && !resolvedTerm.equals(row.term())) {
                    continue;
                }
                rows.add(new PopulationRawRow(
                        row.campus(),
                        row.year(),
                        row.term(),
                        row.ageBand(),
                        row.male(),
                        row.female(),
                        null,
                        null
                ));
            }
            return new PopulationRawData(PopulationRawType.AGGREGATED, rows, extractYears(rows));
        }

        List<PopulationRawRow> rows = new ArrayList<>();
        for (StudentRow row : loaded.students()) {
            if (StringUtils.hasText(resolvedCampus) && !resolvedCampus.equals(row.campus())) {
                continue;
            }
            if (StringUtils.hasText(resolvedYear) && StringUtils.hasText(row.year()) && !resolvedYear.equals(row.year())) {
                continue;
            }
            if (StringUtils.hasText(resolvedTerm) && StringUtils.hasText(row.term()) && !resolvedTerm.equals(row.term())) {
                continue;
            }
            rows.add(new PopulationRawRow(
                    row.campus(),
                    row.year(),
                    row.term(),
                    null,
                    null,
                    null,
                    row.birthYear(),
                    genderToLabel(row.gender())
            ));
        }
        return new PopulationRawData(PopulationRawType.STUDENT, rows, extractYears(rows));
    }

    private Cache getOrLoadCache() {
        Path path = resolveFilePath();
        long lastModified = getLastModifiedMillis(path);

        Cache existing = cache;
        if (existing != null && existing.isSameFile(path, lastModified)) {
            return existing;
        }

        synchronized (cacheLock) {
            Cache second = cache;
            if (second != null && second.isSameFile(path, lastModified)) {
                return second;
            }
            cache = loadFromExcel(path, lastModified);
            return cache;
        }
    }

    private Cache loadFromExcel(Path path, long lastModified) {
        List<StudentRow> studentRows = new ArrayList<>();
        List<AgeAggregateRow> aggregatedRows = new ArrayList<>();

        try (InputStream in = Files.newInputStream(path); Workbook workbook = WorkbookFactory.create(in)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                detectAndParseSheet(sheet, studentRows, aggregatedRows);
            }
        } catch (Exception e) {
            throw new IllegalStateException("캠퍼스 학생비율(인구) 엑셀을 읽지 못했습니다. 파일 경로를 확인해 주세요. path=" + path, e);
        }

        if (studentRows.isEmpty() && aggregatedRows.isEmpty()) {
            throw new IllegalStateException("엑셀에서 캠퍼스/연령/성별 데이터를 찾지 못했습니다. (필요 컬럼: 캠퍼스 + 생년월일 + 성별) 또는 (캠퍼스 + 연령대 + 남/여 또는 성별+인원)");
        }

        return new Cache(path, lastModified, List.copyOf(studentRows), List.copyOf(aggregatedRows));
    }

    private void detectAndParseSheet(Sheet sheet, List<StudentRow> studentRows, List<AgeAggregateRow> aggregatedRows) {
        Header header = findHeader(sheet);
        if (header == null) {
            return;
        }

        if (header.type == SheetType.STUDENT_ROWS) {
            parseStudentRows(sheet, header, studentRows);
            return;
        }
        if (header.type == SheetType.AGGREGATED_WIDE) {
            parseAggregatedWide(sheet, header, aggregatedRows);
            return;
        }
        if (header.type == SheetType.AGGREGATED_PIVOT) {
            parseAggregatedPivot(sheet, header, aggregatedRows);
        }
    }

    private Header findHeader(Sheet sheet) {
        // 왜: 엑셀은 상단에 제목/설명 행이 있는 경우가 많아서, 앞쪽 50행 정도에서 헤더를 찾아야 합니다.
        for (int r = 0; r <= Math.min(sheet.getLastRowNum(), 50); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }

            Map<String, Integer> headerMap = buildHeaderMap(row);
            Integer campusIdx = findIndex(headerMap, HeaderKey.CAMPUS);
            if (campusIdx == null) {
                continue;
            }

            Integer yearIdx = findIndex(headerMap, HeaderKey.YEAR);
            Integer termIdx = findIndex(headerMap, HeaderKey.TERM);

            Integer birthdayIdx = findIndex(headerMap, HeaderKey.BIRTHDAY);
            Integer genderIdx = findIndex(headerMap, HeaderKey.GENDER);

            Integer ageBandIdx = findIndex(headerMap, HeaderKey.AGE_BAND);
            Integer maleIdx = findIndex(headerMap, HeaderKey.MALE_COUNT);
            Integer femaleIdx = findIndex(headerMap, HeaderKey.FEMALE_COUNT);
            Integer countIdx = findIndex(headerMap, HeaderKey.COUNT);

            // 1) 학생 로스터(1행=1학생) 형태
            if (birthdayIdx != null && genderIdx != null) {
                return new Header(SheetType.STUDENT_ROWS, r, campusIdx, yearIdx, termIdx, birthdayIdx, genderIdx, null, null, null, null);
            }

            // 2) 집계 Wide 형태: (캠퍼스, 연령대, 남, 여)
            if (ageBandIdx != null && maleIdx != null && femaleIdx != null) {
                return new Header(SheetType.AGGREGATED_WIDE, r, campusIdx, yearIdx, termIdx, null, null, ageBandIdx, maleIdx, femaleIdx, null);
            }

            // 3) 집계 Pivot 형태: (캠퍼스, 연령대, 성별, 인원)
            if (ageBandIdx != null && genderIdx != null && countIdx != null) {
                return new Header(SheetType.AGGREGATED_PIVOT, r, campusIdx, yearIdx, termIdx, null, genderIdx, ageBandIdx, null, null, countIdx);
            }
        }
        return null;
    }

    private void parseStudentRows(Sheet sheet, Header header, List<StudentRow> out) {
        for (int r = header.headerRowIndex + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }

            String campus = normalizeCampus(getCellString(row, header.campusIndex));
            if (!StringUtils.hasText(campus)) {
                continue;
            }

            Integer birthYear = parseBirthYear(row.getCell(header.birthdayIndex));
            Gender gender = parseGender(getCellString(row, header.genderIndex));
            if (birthYear == null || gender == null) {
                continue;
            }

            String year = header.yearIndex == null ? null : normalizeSimple(getCellString(row, header.yearIndex));
            String term = header.termIndex == null ? null : normalizeSimple(getCellString(row, header.termIndex));

            out.add(new StudentRow(campus, emptyToNull(year), emptyToNull(term), birthYear, gender));
        }
    }

    private void parseAggregatedWide(Sheet sheet, Header header, List<AgeAggregateRow> out) {
        for (int r = header.headerRowIndex + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }

            String campus = normalizeCampus(getCellString(row, header.campusIndex));
            if (!StringUtils.hasText(campus)) {
                continue;
            }

            String ageBand = normalizeAgeBand(getCellString(row, header.ageBandIndex));
            if (!StringUtils.hasText(ageBand)) {
                continue;
            }

            Long male = parseLong(row.getCell(header.maleIndex));
            Long female = parseLong(row.getCell(header.femaleIndex));
            if (male == null && female == null) {
                continue;
            }

            String year = header.yearIndex == null ? null : normalizeSimple(getCellString(row, header.yearIndex));
            String term = header.termIndex == null ? null : normalizeSimple(getCellString(row, header.termIndex));

            out.add(new AgeAggregateRow(campus, emptyToNull(year), emptyToNull(term), ageBand, male == null ? 0L : male, female == null ? 0L : female));
        }
    }

    private void parseAggregatedPivot(Sheet sheet, Header header, List<AgeAggregateRow> out) {
        for (int r = header.headerRowIndex + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }

            String campus = normalizeCampus(getCellString(row, header.campusIndex));
            if (!StringUtils.hasText(campus)) {
                continue;
            }

            String ageBand = normalizeAgeBand(getCellString(row, header.ageBandIndex));
            if (!StringUtils.hasText(ageBand)) {
                continue;
            }

            Gender gender = parseGender(getCellString(row, header.genderIndex));
            if (gender == null) {
                continue;
            }

            Long count = parseLong(row.getCell(header.countIndex));
            if (count == null) {
                continue;
            }

            String year = header.yearIndex == null ? null : normalizeSimple(getCellString(row, header.yearIndex));
            String term = header.termIndex == null ? null : normalizeSimple(getCellString(row, header.termIndex));

            long male = gender == Gender.MALE ? count : 0L;
            long female = gender == Gender.FEMALE ? count : 0L;
            out.add(new AgeAggregateRow(campus, emptyToNull(year), emptyToNull(term), ageBand, male, female));
        }
    }

    private Map<String, Integer> buildHeaderMap(Row row) {
        Map<String, Integer> map = new HashMap<>();
        for (Cell cell : row) {
            String value = formatter.formatCellValue(cell);
            if (!StringUtils.hasText(value)) {
                continue;
            }
            map.put(normalizeHeader(value), cell.getColumnIndex());
        }
        return map;
    }

    private Integer findIndex(Map<String, Integer> headerMap, HeaderKey key) {
        for (String candidate : key.candidates) {
            Integer index = headerMap.get(candidate);
            if (index != null) {
                return index;
            }
        }
        return null;
    }

    private String normalizeHeader(String value) {
        String v = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        v = v.replace(" ", "");
        v = v.replace("_", "");
        v = v.replace("-", "");
        v = v.replace("(", "").replace(")", "");
        return v;
    }

    private String normalizeCampus(String campus) {
        String v = campus == null ? "" : campus.trim();
        if (!StringUtils.hasText(v)) {
            return null;
        }
        if (v.endsWith("캠퍼스")) {
            return v.substring(0, v.length() - "캠퍼스".length()).trim();
        }
        return v;
    }

    private String normalizeSimple(String value) {
        return value == null ? null : value.trim();
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private Integer parseBirthYear(Cell cell) {
        if (cell == null) {
            return null;
        }

        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                LocalDate date = cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                return date.getYear();
            }
        } catch (Exception ignored) {
        }

        String raw = formatter.formatCellValue(cell);
        if (!StringUtils.hasText(raw)) {
            return null;
        }

        String value = raw.trim();
        if (value.matches("^\\d{8}$")) {
            return Integer.parseInt(value.substring(0, 4));
        }
        if (value.matches("^\\d{4}[-./]\\d{1,2}[-./]\\d{1,2}$")) {
            return Integer.parseInt(value.substring(0, 4));
        }
        if (value.matches("^\\d{4}$")) {
            return Integer.parseInt(value);
        }
        return null;
    }

    private List<Integer> extractYears(List<PopulationRawRow> rows) {
        return rows.stream()
                .map(PopulationRawRow::year)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(v -> v.matches("^\\d{4}$"))
                .map(Integer::valueOf)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    private String genderToLabel(Gender gender) {
        if (gender == Gender.MALE) return "남";
        if (gender == Gender.FEMALE) return "여";
        return null;
    }

    private Long parseLong(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return (long) cell.getNumericCellValue();
        }
        String raw = formatter.formatCellValue(cell);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim().replace(",", "");
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getCellString(Row row, int cellIndexZeroBased) {
        Cell cell = row.getCell(cellIndexZeroBased);
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Gender parseGender(String genderValue) {
        if (!StringUtils.hasText(genderValue)) {
            return null;
        }

        String v = genderValue.trim();
        if ("1".equals(v) || "남".equals(v) || "남성".equals(v) || "M".equalsIgnoreCase(v) || "MALE".equalsIgnoreCase(v)) {
            return Gender.MALE;
        }
        if ("2".equals(v) || "여".equals(v) || "여성".equals(v) || "F".equalsIgnoreCase(v) || "FEMALE".equalsIgnoreCase(v)) {
            return Gender.FEMALE;
        }
        return null;
    }

    private String normalizeAgeBand(String ageBandValue) {
        if (!StringUtils.hasText(ageBandValue)) {
            return null;
        }

        String v = ageBandValue.trim();

        // 왜: SGIS/KOSIS 코드 형태(31~36/40)도 들어올 수 있어, 공통 라벨로 변환합니다.
        if (v.matches("^\\d{2}$")) {
            return switch (v) {
                case "31" -> "10대";
                case "32" -> "20대";
                case "33" -> "30대";
                case "34" -> "40대";
                case "35" -> "50대";
                case "36", "40" -> "60대 이상";
                default -> null;
            };
        }

        if (v.contains("10")) return "10대";
        if (v.contains("20")) return "20대";
        if (v.contains("30")) return "30대";
        if (v.contains("40")) return "40대";
        if (v.contains("50")) return "50대";
        if (v.contains("60") || v.contains("70") || v.contains("이상")) return "60대 이상";

        return null;
    }

    private Path resolveFilePath() {
        String filePath = properties.getStudentPopulationFile();
        if (!StringUtils.hasText(filePath)) {
            return findDefaultFile()
                    .orElseThrow(() -> new IllegalStateException("statistics.data.student-population-file 설정이 없습니다. (환경변수: STATISTICS_STUDENT_POPULATION_FILE)"));
        }

        Path configured = Path.of(filePath);
        return StatisticsFileLocator.tryResolve(filePath)
                .orElseThrow(() -> new IllegalStateException(
                        "캠퍼스 학생비율(인구) 엑셀 파일을 찾을 수 없습니다. path=" + configured + " (현재작업폴더=" + Path.of("").toAbsolutePath() + ")"
                ));
    }

    private Optional<Path> findDefaultFile() {
        // 왜: 사용자가 환경변수/설정 파일을 따로 지정하지 않아도,
        //     통계 폴더(../통계)에 "재학생/인구/성별/연령" 관련 파일을 두면 자동으로 잡히게 합니다.
        Optional<Path> dirOpt = StatisticsFileLocator.findStatisticsDirectory();
        if (dirOpt.isEmpty()) return Optional.empty();
        Path dir = dirOpt.get();

        try (var stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".xlsx") || name.endsWith(".xls");
                    })
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.contains("재학생") || name.contains("학생") || name.contains("인구") || name.contains("연령") || name.contains("성별") || name.contains("성비");
                    })
                    .sorted(Comparator.comparingLong(this::getLastModifiedMillis).reversed())
                    .findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private long getLastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception e) {
            return -1L;
        }
    }

    private static String determineAgeBand(int age) {
        if (age >= 10 && age <= 19) return "10대";
        if (age >= 20 && age <= 29) return "20대";
        if (age >= 30 && age <= 39) return "30대";
        if (age >= 40 && age <= 49) return "40대";
        if (age >= 50 && age <= 59) return "50대";
        if (age >= 60) return "60대 이상";
        return null;
    }

    private enum Gender {
        MALE,
        FEMALE
    }

    private enum SheetType {
        STUDENT_ROWS,
        AGGREGATED_WIDE,
        AGGREGATED_PIVOT
    }

    private enum HeaderKey {
        CAMPUS(Set.of("캠퍼스", "캠퍼스명", "campus", "campusname", "campusnm")),
        YEAR(Set.of("연도", "년도", "학년도", "year", "openyear")),
        TERM(Set.of("학기", "term", "openterm")),
        BIRTHDAY(Set.of("생년월일", "birthday", "birthdate", "birth")),
        GENDER(Set.of("성별", "gender", "sex")),
        AGE_BAND(Set.of("연령대", "나이대", "ageband", "agegroup", "agetype", "age_type")),
        MALE_COUNT(Set.of("남", "남성", "male", "m")),
        FEMALE_COUNT(Set.of("여", "여성", "female", "f")),
        COUNT(Set.of("인원", "학생수", "재학생수", "count", "cnt"));

        private final Set<String> candidates;

        HeaderKey(Set<String> candidates) {
            this.candidates = candidates;
        }
    }

    private record Header(
            SheetType type,
            int headerRowIndex,
            int campusIndex,
            Integer yearIndex,
            Integer termIndex,
            Integer birthdayIndex,
            Integer genderIndex,
            Integer ageBandIndex,
            Integer maleIndex,
            Integer femaleIndex,
            Integer countIndex
    ) {
    }

    private record StudentRow(String campus, String year, String term, int birthYear, Gender gender) {
    }

    private record AgeAggregateRow(String campus, String year, String term, String ageBand, long male, long female) {
    }

    public record GenderCount(long male, long female) {
        public long total() {
            return male + female;
        }

        public static GenderCount empty() {
            return new GenderCount(0L, 0L);
        }
    }

    private record Cache(Path path, long lastModified, List<StudentRow> students, List<AgeAggregateRow> aggregates) {
        boolean isSameFile(Path other, long otherLastModified) {
            return path.equals(other) && lastModified == otherLastModified;
        }

        Map<String, GenderCount> countByAgeBandAndGender(String campus, String year, String term, int baseYear) {
            Map<String, GenderCount> result = new LinkedHashMap<>();
            for (String band : List.of("10대", "20대", "30대", "40대", "50대", "60대 이상")) {
                result.put(band, GenderCount.empty());
            }

            // 1) 집계형 데이터가 있으면 그걸 우선 사용합니다.
            if (!aggregates.isEmpty()) {
                for (AgeAggregateRow row : aggregates) {
                    // 왜: campus가 비어있으면(=전체 캠퍼스) 캠퍼스 필터 없이 전부 합산합니다.
                    if (StringUtils.hasText(campus) && !campus.equals(row.campus())) {
                        continue;
                    }
                    if (StringUtils.hasText(year) && StringUtils.hasText(row.year()) && !year.equals(row.year())) {
                        continue;
                    }
                    if (StringUtils.hasText(term) && StringUtils.hasText(row.term()) && !term.equals(row.term())) {
                        continue;
                    }

                    GenderCount existing = result.getOrDefault(row.ageBand(), GenderCount.empty());
                    result.put(row.ageBand(), new GenderCount(existing.male() + row.male(), existing.female() + row.female()));
                }
                return result;
            }

            // 2) 학생 로스터(1행=1학생) 기반으로 연령대를 계산합니다.
            for (StudentRow row : students) {
                if (StringUtils.hasText(campus) && !campus.equals(row.campus())) {
                    continue;
                }
                if (StringUtils.hasText(year) && StringUtils.hasText(row.year()) && !year.equals(row.year())) {
                    continue;
                }
                if (StringUtils.hasText(term) && StringUtils.hasText(row.term()) && !term.equals(row.term())) {
                    continue;
                }

                int age = baseYear - row.birthYear();
                String ageBand = determineAgeBand(age);
                if (!StringUtils.hasText(ageBand)) {
                    continue;
                }

                GenderCount existing = result.getOrDefault(ageBand, GenderCount.empty());
                if (row.gender() == Gender.MALE) {
                    result.put(ageBand, new GenderCount(existing.male() + 1, existing.female()));
                } else if (row.gender() == Gender.FEMALE) {
                    result.put(ageBand, new GenderCount(existing.male(), existing.female() + 1));
                }
            }
            return result;
        }
    }

    public enum PopulationRawType {
        AGGREGATED,
        STUDENT
    }

    public record PopulationRawData(PopulationRawType type, List<PopulationRawRow> rows, List<Integer> availableYears) {
    }

    public record PopulationRawRow(
            String campus,
            String year,
            String term,
            String ageBand,
            Long male,
            Long female,
            Integer birthYear,
            String gender
    ) {
    }
}
