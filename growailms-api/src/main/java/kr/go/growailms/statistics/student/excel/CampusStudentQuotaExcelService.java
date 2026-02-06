package kr.go.growailms.statistics.student.excel;

import kr.go.growailms.statistics.internalstats.StatisticsDataProperties;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import kr.go.growailms.statistics.util.StatisticsFileLocator;

@Service
public class CampusStudentQuotaExcelService {
    // 왜: "캠퍼스 학생 비율"을 DB가 아니라, 현재 통계 폴더의 엑셀만으로 바로 보여줘야 하는 요구가 있습니다.
    // - 입시율관리.xlsx에는 캠퍼스/학과별 정원(=규모)이 들어있어서, 학생수 대체 지표로 활용할 수 있습니다.
    // - 실제 재학생수(현원)가 있는 별도 파일이 추후 생기면, 이 서비스만 교체/확장하면 됩니다.

    private final StatisticsDataProperties properties;
    private final DataFormatter formatter;

    private volatile Cache cache;
    private final Object cacheLock = new Object();

    public CampusStudentQuotaExcelService(StatisticsDataProperties properties) {
        this.properties = properties;
        this.formatter = new DataFormatter(Locale.KOREA);
    }

    public List<CampusDeptQuota> getCampusDeptQuotas() {
        Path path = resolveAdmissionFilePath();
        long lastModified = getLastModifiedMillis(path);

        Cache existing = cache;
        if (existing != null && existing.isSameFile(path, lastModified)) {
            return existing.rows();
        }

        synchronized (cacheLock) {
            Cache second = cache;
            if (second != null && second.isSameFile(path, lastModified)) {
                return second.rows();
            }
            Cache loaded = loadFromExcel(path, lastModified);
            cache = loaded;
            return loaded.rows();
        }
    }

    private Cache loadFromExcel(Path path, long lastModified) {
        try (InputStream in = Files.newInputStream(path); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = resolveAdmissionSheet(workbook);

            List<CampusDeptQuota> rows = new ArrayList<>();
            for (Row row : sheet) {
                // 왜: 상단 제목/헤더 영역을 건너뛰고, 실제 데이터 행만 처리합니다.
                if (row.getRowNum() < 8) {
                    continue;
                }

                String campus = normalizeCampus(getCellString(row, 1));
                String dept = normalizeDept(getCellString(row, 3));
                Long quota = getCellLong(row, 5);

                if (!StringUtils.hasText(campus) || !StringUtils.hasText(dept)) {
                    continue;
                }
                if (isSummaryDept(dept)) {
                    continue;
                }
                if (quota == null || quota <= 0) {
                    continue;
                }

                rows.add(new CampusDeptQuota(campus, dept, quota));
            }

            rows.sort(Comparator
                    .comparing(CampusDeptQuota::campus)
                    .thenComparing(CampusDeptQuota::dept));

            return new Cache(path, lastModified, List.copyOf(rows));
        } catch (Exception e) {
            throw new IllegalStateException("캠퍼스 학생 정원(입시율관리) 엑셀을 읽지 못했습니다. statistics.data.admission-file 경로를 확인해 주세요.", e);
        }
    }

    private Sheet resolveAdmissionSheet(Workbook workbook) {
        Sheet named = workbook.getSheet("2025.11.25. 24시");
        return Objects.requireNonNullElse(named, workbook.getSheetAt(0));
    }

    private Path resolveAdmissionFilePath() {
        String file = properties.getAdmissionFile();
        if (!StringUtils.hasText(file)) {
            throw new IllegalStateException("statistics.data.admission-file 설정이 없습니다.");
        }

        Path configured = Path.of(file);
        return StatisticsFileLocator.tryResolve(file)
                .orElseThrow(() -> new IllegalStateException(
                        "입시율관리.xlsx 파일을 찾을 수 없습니다. path=" + configured + " (현재작업폴더=" + Path.of("").toAbsolutePath() + ")"
                ));
    }

    private long getLastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception e) {
            return -1L;
        }
    }

    private String normalizeCampus(String campus) {
        if (!StringUtils.hasText(campus)) {
            return null;
        }
        String v = campus.trim();
        if (v.endsWith("캠퍼스")) {
            return v.substring(0, v.length() - "캠퍼스".length()).trim();
        }
        return v;
    }

    private String normalizeDept(String dept) {
        if (!StringUtils.hasText(dept)) {
            return null;
        }
        String v = dept.trim();
        int index = v.indexOf('(');
        if (index > 0) {
            return v.substring(0, index).trim();
        }
        return v;
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

    private Long getCellLong(Row row, int cellIndexZeroBased) {
        Cell cell = row.getCell(cellIndexZeroBased);
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

    private record Cache(Path path, long lastModified, List<CampusDeptQuota> rows) {
        boolean isSameFile(Path other, long otherLastModified) {
            return path.equals(other) && lastModified == otherLastModified;
        }
    }

    public record CampusDeptQuota(String campus, String dept, long quota) {
    }
}
