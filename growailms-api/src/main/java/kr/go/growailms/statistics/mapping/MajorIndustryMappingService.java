package kr.go.growailms.statistics.mapping;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kr.go.growailms.statistics.util.StatisticsFileLocator;

@Service
public class MajorIndustryMappingService {
    // 왜: "캠퍼스 전공(학과) 재학생 비율"을 "기술업종 분류(첨단/고기술/…)"로 변환하고,
    //     지역 산업 비율(사업체 수 등)과 같은 축에서 비교하려면 매핑 기준이 반드시 필요합니다.

    private static final Pattern INDUSTRY_CODE_PATTERN = Pattern.compile("\\((\\d+)\\)");

    private final StatisticsMappingProperties properties;
    private final DataFormatter formatter;

    private volatile MappingData cached;
    private final Object cacheLock = new Object();

    public MajorIndustryMappingService(StatisticsMappingProperties properties) {
        this.properties = properties;
        this.formatter = new DataFormatter(Locale.KOREA);
    }

    public List<CampusGroup> getCampusGroups() {
        return getOrLoadMapping().campusGroups();
    }

    public Optional<String> findCategoryByCampusAndDept(String campusName, String deptName) {
        if (!StringUtils.hasText(campusName) || !StringUtils.hasText(deptName)) {
            return Optional.empty();
        }

        String campusKey = normalizeCampusName(campusName);
        String deptKey = normalizeDeptName(deptName);
        String key = campusKey + "|" + deptKey;
        return Optional.ofNullable(getOrLoadMapping().categoryByCampusDept().get(key));
    }

    public Map<String, List<String>> getSgisClassCodesByCategory() {
        return getOrLoadMapping().sgisClassCodesByCategory();
    }

    private MappingData getOrLoadMapping() {
        MappingData existing = cached;
        if (existing != null) {
            return existing;
        }

        synchronized (cacheLock) {
            if (cached != null) {
                return cached;
            }

            cached = loadMappingFromExcel();
            return cached;
        }
    }

    private MappingData loadMappingFromExcel() {
        Path path = resolveMappingFilePath();

        try (InputStream in = Files.newInputStream(path); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = resolveTargetSheet(workbook);

            List<CampusGroup> campusGroups = buildCampusGroups(sheet);
            Map<String, String> categoryByCampusDept = buildCategoryByCampusDept(sheet);
            Map<String, List<String>> sgisClassCodesByCategory = buildSgisClassCodesByCategory(sheet);

            return new MappingData(campusGroups, categoryByCampusDept, sgisClassCodesByCategory);
        } catch (Exception e) {
            // 왜: 파일이 없거나(경로 오류), 엑셀 구조가 깨졌을 때는 통계 화면이 의미 있게 동작할 수 없어서 명확히 실패합니다.
            throw new IllegalStateException("전공-산업 매핑 엑셀을 읽지 못했습니다. statistics.mapping.major-industry-file 경로를 확인해 주세요.", e);
        }
    }

    private Path resolveMappingFilePath() {
        String file = properties.getMajorIndustryFile();
        if (!StringUtils.hasText(file)) {
            throw new IllegalStateException("전공-산업 매핑 파일 설정이 없습니다. statistics.mapping.major-industry-file 을 설정해 주세요.");
        }

        Path configured = Path.of(file);
        return StatisticsFileLocator.tryResolve(file)
                .orElseThrow(() -> new IllegalStateException(
                        "전공-산업 매핑 파일을 찾을 수 없습니다. 경로=" + configured + " (현재작업폴더=" + Path.of("").toAbsolutePath() + ")"
                ));
    }

    private Sheet resolveTargetSheet(Workbook workbook) {
        // 왜: 현재 문서 구조가 고정되어 있지만, 시트명이 바뀌는 경우를 대비해 안전하게 찾습니다.
        Sheet named = workbook.getSheet("통계 기술업종 매칭요청");
        return (named != null) ? named : workbook.getSheetAt(0);
    }

    private List<CampusGroup> buildCampusGroups(Sheet sheet) {
        // 왜: 화면의 "캠퍼스 그룹(I~VII대학/특성화)" 선택 목록을 매핑 파일 기준으로 구성합니다.
        Map<String, Set<String>> campusesByGroupCode = new LinkedHashMap<>();

        for (Row row : sheet) {
            if (row.getRowNum() < 3) {
                continue;
            }

            String university = getCellString(row, 1);
            String campus = getCellString(row, 2);
            if (!StringUtils.hasText(university) || !StringUtils.hasText(campus)) {
                continue;
            }

            String groupCode = toCampusGroupCode(university);
            String campusName = normalizeCampusName(campus);

            campusesByGroupCode.computeIfAbsent(groupCode, k -> new LinkedHashSet<>()).add(campusName);
        }

        List<CampusGroup> groups = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : campusesByGroupCode.entrySet()) {
            String code = entry.getKey();
            groups.add(new CampusGroup(code, toCampusGroupName(code), List.copyOf(entry.getValue())));
        }
        return groups;
    }

    private Map<String, String> buildCategoryByCampusDept(Sheet sheet) {
        // 왜: DB의 학과(전공) 명칭을 "기술업종 분류(7개)"로 바꿔야 산업 비교가 가능합니다.
        // - 키: 캠퍼스|학과 (둘 다 정규화해서 비교 안정성을 높입니다)
        Map<String, String> result = new LinkedHashMap<>();

        for (Row row : sheet) {
            if (row.getRowNum() < 3) {
                continue;
            }

            String campus = getCellString(row, 2);
            String dept = getCellString(row, 5);
            String category = getCellString(row, 7);

            if (!StringUtils.hasText(campus) || !StringUtils.hasText(dept) || !StringUtils.hasText(category)) {
                continue;
            }

            String normalizedCategory = category.trim();
            if ("-".equals(normalizedCategory)) {
                continue;
            }

            String normalizedDept = normalizeDeptName(dept);
            if (isSummaryRowDept(normalizedDept)) {
                continue;
            }

            String key = normalizeCampusName(campus) + "|" + normalizedDept;
            result.putIfAbsent(key, normalizedCategory);
        }

        return result;
    }

    private Map<String, List<String>> buildSgisClassCodesByCategory(Sheet sheet) {
        // 왜: 지역 산업 비율을 계산할 때, "어떤 산업코드 묶음이 첨단/고기술/…"인지가 필요합니다.
        // - 엑셀 상단의 "관련산업분류(10차)" 목록을 SGIS class_code로 변환해 둡니다.
        Map<String, Set<String>> codesByCategory = new LinkedHashMap<>();

        String currentCategory = null;
        for (Row row : sheet) {
            int rowNum = row.getRowNum();
            if (rowNum < 3) {
                continue;
            }

            String category = getCellString(row, 11);
            if (StringUtils.hasText(category)) {
                currentCategory = category.trim();
            }

            if (!StringUtils.hasText(currentCategory)) {
                continue;
            }

            String relatedIndustry = getCellString(row, 13);
            if (!StringUtils.hasText(relatedIndustry)) {
                continue;
            }

            List<String> industryCodes = extractIndustryCodes(relatedIndustry);
            if (industryCodes.isEmpty()) {
                continue;
            }

            Set<String> classCodes = codesByCategory.computeIfAbsent(currentCategory, k -> new LinkedHashSet<>());
            for (String code : industryCodes) {
                String classCode = toSgisClassCode(code);
                if (classCode != null) {
                    classCodes.add(classCode);
                }
            }
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : codesByCategory.entrySet()) {
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    private List<String> extractIndustryCodes(String text) {
        Matcher matcher = INDUSTRY_CODE_PATTERN.matcher(text);
        List<String> codes = new ArrayList<>();
        while (matcher.find()) {
            codes.add(matcher.group(1));
        }
        return codes;
    }

    private String toCampusGroupCode(String universityName) {
        // 왜: 화면에서 사용하는 그룹 값(I~VII대학/특성화)을 코드로 고정해 두면,
        //     프론트/백엔드 모두 동일한 기준으로 필터링할 수 있습니다.
        String v = universityName.trim();
        if (v.contains("특성화")) {
            return "SPECIAL";
        }

        if (v.endsWith("Ⅰ")) return "I";
        if (v.endsWith("Ⅱ")) return "II";
        if (v.endsWith("Ⅲ")) return "III";
        if (v.endsWith("Ⅳ")) return "IV";
        if (v.endsWith("Ⅴ")) return "V";
        if (v.endsWith("Ⅵ")) return "VI";
        if (v.endsWith("Ⅶ")) return "VII";

        // 왜: 예상하지 못한 값이 들어오더라도 화면이 깨지지 않도록 기본 그룹으로 묶습니다.
        return "OTHER";
    }

    private String toCampusGroupName(String code) {
        return switch (code) {
            case "I" -> "I대학";
            case "II" -> "II대학";
            case "III" -> "III대학";
            case "IV" -> "IV대학";
            case "V" -> "V대학";
            case "VI" -> "VI대학";
            case "VII" -> "VII대학";
            case "SPECIAL" -> "특성화";
            default -> "기타";
        };
    }

    private String normalizeCampusName(String name) {
        String trimmed = name.trim();
        if (trimmed.endsWith("캠퍼스")) {
            return trimmed.substring(0, trimmed.length() - "캠퍼스".length()).trim();
        }
        return trimmed;
    }

    private String normalizeDeptName(String name) {
        // 왜: DB/엑셀에서 학과명이 "OO과(세부트랙)"처럼 괄호가 붙는 케이스가 있어, 기본 학과명으로 정규화합니다.
        String trimmed = name.trim();
        int index = trimmed.indexOf('(');
        if (index > 0) {
            return trimmed.substring(0, index).trim();
        }
        return trimmed;
    }

    private boolean isSummaryRowDept(String deptName) {
        String v = deptName.trim();
        // 왜: 엑셀에는 "소계/총계" 같은 집계 행이 섞여 있어서, 실제 학과 행만 골라야 합니다.
        // - "기계과"처럼 '계'가 포함된 정상 학과를 잘못 제외하지 않도록 조건을 보수적으로 둡니다.
        if (v.equals("계") || v.equals("총계") || v.equals("소계")) {
            return true;
        }
        return v.endsWith("소계") || v.contains("별 소계");
    }

    private String toSgisClassCode(String industryCode) {
        // 왜: SGIS OpenAPI는 "섹션 문자 + 코드" 형태(class_code=C26, J612 등)를 사용합니다.
        // - 엑셀은 숫자만 제공하므로, 앞의 2자리 규칙으로 섹션을 결정합니다.
        String code = industryCode.trim();
        if (!code.matches("\\d+")) {
            return null;
        }

        if (code.length() < 2) {
            return null;
        }

        int prefix2 = Integer.parseInt(code.substring(0, 2));
        if (prefix2 >= 10 && prefix2 <= 34) {
            return "C" + code;
        }
        if (prefix2 >= 58 && prefix2 <= 63) {
            return "J" + code;
        }
        if (prefix2 >= 70 && prefix2 <= 73) {
            return "M" + code;
        }
        if (prefix2 >= 74 && prefix2 <= 76) {
            return "N" + code;
        }

        return null;
    }

    private String getCellString(Row row, int cellIndexZeroBased) {
        Cell cell = row.getCell(cellIndexZeroBased);
        if (cell == null) {
            return null;
        }

        String value = formatter.formatCellValue(cell);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record MappingData(
            List<CampusGroup> campusGroups,
            Map<String, String> categoryByCampusDept,
            Map<String, List<String>> sgisClassCodesByCategory
    ) {
    }

    public record CampusGroup(String code, String name, List<String> campuses) {
    }
}
