package kr.go.growailms.job.code;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class JobKoreaCodeCatalog {
    // 왜: 잡코리아는 Work24와 코드 체계(지역/직종)가 달라서, 화면에서 쓸 코드 목록을 별도로 관리해야 합니다.

    private JobKoreaCodeCatalog() {
    }

    private static final Map<String, String> TOP_LEVEL_AREA_CODES = Map.ofEntries(
        Map.entry("I000", "서울특별시"),
        Map.entry("K000", "인천광역시"),
        Map.entry("B000", "경기도"),
        Map.entry("A000", "강원도"),
        Map.entry("C000", "경상남도"),
        Map.entry("D000", "경상북도"),
        Map.entry("E000", "광주광역시"),
        Map.entry("F000", "대구광역시"),
        Map.entry("G000", "대전광역시"),
        Map.entry("H000", "부산광역시"),
        Map.entry("J000", "울산광역시"),
        Map.entry("L000", "전라남도"),
        Map.entry("M000", "전라북도"),
        Map.entry("O000", "충청남도"),
        Map.entry("P000", "충청북도"),
        Map.entry("N000", "제주특별자치도"),
        Map.entry("1000", "세종특별자치시"),
        Map.entry("Q000", "전국")
    );

    private static final Map<String, String> AREA_PREFIX_TO_NAME = Map.ofEntries(
        Map.entry("A", "강원도"),
        Map.entry("B", "경기도"),
        Map.entry("C", "경상남도"),
        Map.entry("D", "경상북도"),
        Map.entry("E", "광주광역시"),
        Map.entry("F", "대구광역시"),
        Map.entry("G", "대전광역시"),
        Map.entry("H", "부산광역시"),
        Map.entry("I", "서울특별시"),
        Map.entry("J", "울산광역시"),
        Map.entry("K", "인천광역시"),
        Map.entry("L", "전라남도"),
        Map.entry("M", "전라북도"),
        Map.entry("N", "제주특별자치도"),
        Map.entry("O", "충청남도"),
        Map.entry("P", "충청북도")
    );

    private static final Lazy<List<CodeItem>> RBCD = new Lazy<>(JobKoreaCodeCatalog::loadRbcd);
    private static final Lazy<Map<String, List<CodeItem>>> RPCD_BY_PARENT = new Lazy<>(JobKoreaCodeCatalog::loadRpcdByParent);
    private static final Lazy<Map<String, String>> RPCD_TO_PARENT = new Lazy<>(JobKoreaCodeCatalog::loadRpcdToParent);
    private static final Lazy<Map<String, CodeItem>> AREA_BY_CODE = new Lazy<>(JobKoreaCodeCatalog::loadAreaByCode);
    private static final Lazy<Map<String, String>> RBCD_NAME_BY_CODE = new Lazy<>(JobKoreaCodeCatalog::loadRbcdNameByCode);
    private static final Lazy<Map<String, String>> RPCD_NAME_BY_CODE = new Lazy<>(JobKoreaCodeCatalog::loadRpcdNameByCode);

    public static List<CodeItem> topLevelAreaCodes() {
        List<CodeItem> out = new ArrayList<>();
        for (Map.Entry<String, String> entry : TOP_LEVEL_AREA_CODES.entrySet()) {
            out.add(new CodeItem(entry.getKey(), entry.getValue(), null));
        }
        out.sort(Comparator.comparing(CodeItem::code));
        return out;
    }

    public static String resolveAreaDisplayName(String rawAreaCode) {
        if (rawAreaCode == null) return "";
        List<String> codes = tokenizeCommaCodes(rawAreaCode);
        if (codes.isEmpty()) return "";

        String first = codes.get(0);
        if (first.isBlank() || "0".equals(first)) return "지역무관";

        String firstLabel = resolveSingleAreaDisplayName(first);
        if (codes.size() <= 1) return firstLabel;

        // 왜: 잡코리아는 복수 근무지가 콤마로 함께 올 수 있어, 카드 UI에서는 첫 지역 + "외 N곳"으로 간단히 표시합니다.
        return firstLabel + " 외 " + (codes.size() - 1) + "곳";
    }

    public static List<CodeItem> rbcd() {
        return RBCD.get();
    }

    public static List<CodeItem> rpcd(String parentRbcd) {
        if (parentRbcd == null || parentRbcd.isBlank()) return List.of();
        Map<String, List<CodeItem>> map = RPCD_BY_PARENT.get();
        List<CodeItem> items = map.get(parentRbcd.trim());
        return items == null ? List.of() : items;
    }

    public static String resolveParentRbcdByRpcd(String rpcdCode) {
        if (rpcdCode == null || rpcdCode.isBlank()) return null;
        String code = rpcdCode.trim();
        return RPCD_TO_PARENT.get().get(code);
    }

    public static String resolveOccupationDisplayName(String rawCode) {
        if (rawCode == null) return "";
        String code = rawCode.trim();
        if (code.isBlank()) return "";

        // rbcd(대분류)
        if (code.length() == 5) {
            return RBCD_NAME_BY_CODE.get().getOrDefault(code, code);
        }

        // rpcd(소분류)
        String rpcdName = RPCD_NAME_BY_CODE.get().getOrDefault(code, code);
        String parent = resolveParentRbcdByRpcd(code);
        if (parent == null || parent.isBlank()) return rpcdName;

        String rbcdName = RBCD_NAME_BY_CODE.get().getOrDefault(parent, parent);
        return rbcdName + " / " + rpcdName;
    }

    private static List<CodeItem> loadRbcd() {
        List<String[]> rows = readTsv("jobkorea/rbcd.tsv", 2);
        List<CodeItem> out = new ArrayList<>();
        for (String[] row : rows) {
            out.add(new CodeItem(row[0], normalizeLabel(row[1]), null));
        }
        out.sort(Comparator.comparing(CodeItem::code));
        return out;
    }

    private static Map<String, String> loadRbcdNameByCode() {
        List<String[]> rows = readTsv("jobkorea/rbcd.tsv", 2);
        Map<String, String> out = new LinkedHashMap<>();
        for (String[] row : rows) {
            out.put(row[0], normalizeLabel(row[1]));
        }
        return out;
    }

    private static Map<String, String> loadRpcdNameByCode() {
        List<String[]> rows = readTsv("jobkorea/rpcd.tsv", 3);
        Map<String, String> out = new LinkedHashMap<>();
        for (String[] row : rows) {
            // row[1] = rpcd, row[2] = name
            out.put(row[1], normalizeLabel(row[2]));
        }
        return out;
    }

    private static Map<String, List<CodeItem>> loadRpcdByParent() {
        List<String[]> rows = readTsv("jobkorea/rpcd.tsv", 3);
        Map<String, List<CodeItem>> out = new LinkedHashMap<>();
        for (String[] row : rows) {
            String parent = row[0];
            String code = row[1];
            String name = normalizeLabel(row[2]);
            out.computeIfAbsent(parent, k -> new ArrayList<>()).add(new CodeItem(code, name, parent));
        }

        for (List<CodeItem> list : out.values()) {
            list.sort(Comparator.comparing(CodeItem::code));
        }

        return out;
    }

    private static Map<String, String> loadRpcdToParent() {
        // 왜: 잡코리아 중분류(rpcd)를 검색 파라미터로 쓸 때, 부모 대분류(rbcd)를 같이 보내야 필터링이 확실하게 적용되는 경우가 있습니다.
        // 그래서 rpcd → rbcd(부모) 매핑을 리소스에서 로딩해 둡니다.
        List<String[]> rows = readTsv("jobkorea/rpcd.tsv", 3);
        Map<String, String> out = new LinkedHashMap<>();
        for (String[] row : rows) {
            String parent = row[0];
            String code = row[1];
            if (parent.isBlank() || code.isBlank()) continue;
            out.put(code, parent);
        }
        return out;
    }

    private static Map<String, CodeItem> loadAreaByCode() {
        // 왜: 잡코리아 공고는 AreaCode(예: I180)로만 내려오므로, 화면에서 사람이 읽을 수 있는 지역명으로 바꿔주기 위해 코드표를 로딩합니다.
        List<String[]> rows = readTsv("jobkorea/area.tsv", 3);
        Map<String, CodeItem> out = new LinkedHashMap<>();
        for (String[] row : rows) {
            String parent = row[0];
            String code = row[1];
            String name = normalizeAreaLabel(row[2]);
            out.put(code, new CodeItem(code, name, parent));
        }
        return out;
    }

    private static List<String[]> readTsv(String classpath, int minColumns) {
        InputStream is = JobKoreaCodeCatalog.class.getClassLoader().getResourceAsStream(classpath);
        if (is == null) {
            throw new IllegalStateException("잡코리아 코드 리소스를 찾을 수 없습니다: " + classpath);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            List<String[]> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

                String[] parts = trimmed.split("\\t");
                if (parts.length < minColumns) continue;

                for (int i = 0; i < parts.length; i++) {
                    parts[i] = parts[i] == null ? "" : parts[i].trim();
                }

                boolean hasEmpty = false;
                for (int i = 0; i < minColumns; i++) {
                    if (parts[i].isBlank()) {
                        hasEmpty = true;
                        break;
                    }
                }
                if (hasEmpty) continue;
                rows.add(parts);
            }
            return rows;
        } catch (Exception e) {
            throw new IllegalStateException("잡코리아 코드 리소스 로딩 실패: " + classpath, e);
        }
    }

    private static List<String> tokenizeCommaCodes(String raw) {
        if (raw == null) return List.of();
        String trimmed = raw.trim();
        if (trimmed.isBlank()) return List.of();
        String[] parts = trimmed.split(",");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            String v = part == null ? "" : part.trim();
            if (v.isBlank()) continue;
            out.add(v.toUpperCase(Locale.ROOT));
        }
        return out;
    }

    private static String normalizeLabel(String raw) {
        if (raw == null) return "";
        // 왜: PDF 텍스트 추출 과정에서 “엔터테인 먼트”, “배 송”처럼 단어 내부에 공백이 깨져 들어오는 경우가 있습니다.
        // 코드 표시는 공백 없이도 의미가 충분해서, 여기서는 공백을 제거해 깔끔하게 보여줍니다.
        return raw.replaceAll("\\s+", "");
    }

    private static String normalizeAreaLabel(String raw) {
        if (raw == null) return "";
        // 왜: 지역명은 공백이 의미를 가지므로 “여러 공백 → 한 칸”만 정리합니다.
        return raw.replaceAll("\\s+", " ").trim();
    }

    private static String resolveSingleAreaDisplayName(String areaCode) {
        if (areaCode == null) return "";
        String code = areaCode.trim().toUpperCase(Locale.ROOT);
        if (code.isBlank() || "0".equals(code)) return "지역무관";

        String direct = TOP_LEVEL_AREA_CODES.get(code);
        if (direct != null) return direct;

        CodeItem item = AREA_BY_CODE.get().get(code);
        if (item != null) {
            String parentCode = item.parentCode();
            String parentName = (parentCode == null || parentCode.isBlank())
                ? ""
                : TOP_LEVEL_AREA_CODES.getOrDefault(parentCode, parentCode);

            if (!parentName.isBlank()) return parentName + " " + item.name();
            return item.name();
        }

        // 왜: 코드표가 누락된 값이 오더라도 카드가 깨지지 않게, “시/도” 수준으로라도 추정합니다.
        if (code.length() == 4 && Character.isLetter(code.charAt(0))) {
            String prefix = String.valueOf(Character.toUpperCase(code.charAt(0)));
            String name = AREA_PREFIX_TO_NAME.get(prefix);
            if (name != null) return name;
        }

        return code;
    }

    public record CodeItem(String code, String name, String parentCode) {
        public CodeItem {
            Objects.requireNonNull(code);
            Objects.requireNonNull(name);
        }
    }

    private static final class Lazy<T> {
        private final java.util.function.Supplier<T> supplier;
        private volatile T value;

        private Lazy(java.util.function.Supplier<T> supplier) {
            this.supplier = supplier;
        }

        T get() {
            T v = value;
            if (v != null) return v;
            synchronized (this) {
                if (value == null) {
                    value = supplier.get();
                }
                return value;
            }
        }
    }
}
