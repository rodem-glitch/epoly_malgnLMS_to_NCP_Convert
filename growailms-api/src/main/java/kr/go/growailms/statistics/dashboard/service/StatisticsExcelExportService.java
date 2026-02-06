package kr.go.growailms.statistics.dashboard.service;

import kr.go.growailms.statistics.dashboard.service.IndustryAnalysisService.IndustryAnalysisResponse;
import kr.go.growailms.statistics.dashboard.service.PopulationComparisonService.PopulationComparisonResponse;
import kr.go.growailms.statistics.internalstats.InternalStatisticsService.DepartmentRate;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Service
public class StatisticsExcelExportService {
    // 왜: 화면에서 "엑셀 다운로드" 버튼을 눌렀을 때, CSV가 아니라 실제 xlsx를 내려주면
    //     (1) 한글/숫자 인코딩 이슈가 줄고, (2) 실무 사용자(엑셀) 경험이 더 좋아집니다.

    public byte[] exportInternal(String campus, List<DepartmentRate> employmentRates, List<DepartmentRate> admissionRates) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("내부통계");

            CellStyle percentStyle = createPercentStyle(workbook);

            int rowIndex = 0;
            rowIndex = writeHeaderRow(sheet, rowIndex, List.of("학과", "취업률(%)", "입학충원률(%)"));

            Map<String, Double> employmentMap = toDeptRateMap(employmentRates);
            Map<String, Double> admissionMap = toDeptRateMap(admissionRates);

            TreeSet<String> depts = new TreeSet<>();
            depts.addAll(employmentMap.keySet());
            depts.addAll(admissionMap.keySet());

            for (String dept : depts) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(dept);

                writePercentCell(row, 1, employmentMap.get(dept), percentStyle);
                writePercentCell(row, 2, admissionMap.get(dept), percentStyle);
            }

            autosizeColumns(sheet, 3);
            return toBytes(workbook);
        } catch (Exception e) {
            throw new IllegalStateException("내부 통계 엑셀 생성에 실패했습니다. campus=" + campus, e);
        }
    }

    public byte[] exportPopulation(PopulationComparisonResponse response) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("인구비교");

            CellStyle percentStyle = createPercentStyle(workbook);

            int rowIndex = 0;
            rowIndex = writeHeaderRow(sheet, rowIndex, List.of(
                    "연령대",
                    "행정구역 인구수",
                    "캠퍼스 학생수",
                    "행정구역 인구비율(%)",
                    "캠퍼스 학생비율(%)",
                    "GAP(%p)",
                    "행정구역 남성비율(%)",
                    "캠퍼스 남성비율(%)",
                    "남성비율 GAP(%p)"
            ));

            for (PopulationComparisonService.AgeRow r : response.rows()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(r.ageBand());
                row.createCell(1).setCellValue(r.regionCount());
                row.createCell(2).setCellValue(r.campusCount());
                writePercentCell(row, 3, r.regionRatio(), percentStyle);
                writePercentCell(row, 4, r.campusRatio(), percentStyle);
                writePercentCell(row, 5, r.gap(), percentStyle);
                writePercentCell(row, 6, r.regionMaleRatio(), percentStyle);
                writePercentCell(row, 7, r.campusMaleRatio(), percentStyle);
                writePercentCell(row, 8, r.maleGap(), percentStyle);
            }

            autosizeColumns(sheet, 9);
            return toBytes(workbook);
        } catch (Exception e) {
            throw new IllegalStateException("인구 비교 엑셀 생성에 실패했습니다.", e);
        }
    }

    public byte[] exportIndustry(IndustryAnalysisResponse response) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("산업비교");

            CellStyle percentStyle = createPercentStyle(workbook);

            int rowIndex = 0;
            rowIndex = writeHeaderRow(sheet, rowIndex, List.of(
                    "분야",
                    "행정구역 종사자 수",
                    "캠퍼스 학생 수",
                    "행정구역 종사자 비율(%)",
                    "캠퍼스 학생 비율(%)",
                    "GAP(%p)"
            ));

            for (IndustryAnalysisService.CategoryRow r : response.rows()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(r.category());
                row.createCell(1).setCellValue(r.regionCount());
                row.createCell(2).setCellValue(r.campusCount());
                writePercentCell(row, 3, r.regionRatio(), percentStyle);
                writePercentCell(row, 4, r.campusRatio(), percentStyle);
                writePercentCell(row, 5, r.gap(), percentStyle);
            }

            autosizeColumns(sheet, 6);
            return toBytes(workbook);
        } catch (Exception e) {
            throw new IllegalStateException("산업 비교 엑셀 생성에 실패했습니다.", e);
        }
    }

    public byte[] exportRawData(String sheetName, List<String> headers, List<List<Object>> rows) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName == null ? "로우데이터" : sheetName);

            int rowIndex = 0;
            rowIndex = writeHeaderRow(sheet, rowIndex, headers);

            for (List<Object> rowValues : rows) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < headers.size(); i++) {
                    Object value = (rowValues != null && i < rowValues.size()) ? rowValues.get(i) : null;
                    if (value == null) {
                        row.createCell(i).setCellValue("");
                        continue;
                    }
                    if (value instanceof Number num) {
                        row.createCell(i).setCellValue(num.doubleValue());
                        continue;
                    }
                    row.createCell(i).setCellValue(String.valueOf(value));
                }
            }

            autosizeColumns(sheet, headers == null ? 0 : headers.size());
            return toBytes(workbook);
        } catch (Exception e) {
            throw new IllegalStateException("로우 데이터 엑셀 생성에 실패했습니다.", e);
        }
    }

    private Map<String, Double> toDeptRateMap(List<DepartmentRate> rows) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (DepartmentRate r : rows) {
            map.put(r.dept(), r.rate());
        }
        return map;
    }

    private int writeHeaderRow(Sheet sheet, int rowIndex, List<String> headers) {
        Row header = sheet.createRow(rowIndex++);
        for (int i = 0; i < headers.size(); i++) {
            header.createCell(i).setCellValue(headers.get(i));
        }
        return rowIndex;
    }

    private void writePercentCell(Row row, int cellIndex, Double value, CellStyle percentStyle) {
        if (value == null) {
            row.createCell(cellIndex).setCellValue("");
            return;
        }

        row.createCell(cellIndex).setCellValue(value);
        row.getCell(cellIndex).setCellStyle(percentStyle);
    }

    private CellStyle createPercentStyle(Workbook workbook) {
        DataFormat format = workbook.createDataFormat();
        CellStyle style = workbook.createCellStyle();
        // 왜: 화면/엑셀에서 소수 둘째 자리까지는 보여야 비교(GAP)가 직관적입니다.
        style.setDataFormat(format.getFormat("0.00"));
        return style;
    }

    private void autosizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private byte[] toBytes(Workbook workbook) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
