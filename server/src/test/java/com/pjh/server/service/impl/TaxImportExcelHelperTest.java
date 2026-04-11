package com.pjh.server.service.impl;

import com.pjh.server.common.Result;
import com.pjh.server.entity.TaxRecord;
import com.pjh.server.mapper.TaxRecordMapper;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxImportExcelHelperTest {

    @Mock
    private TaxRecordMapper taxRecordMapper;

    @Test
    void importExcelShouldAcceptMonthQuarterAnnualAndVariousAmounts() throws Exception {
        when(taxRecordMapper.selectCount(any())).thenReturn(0L);

        MockMultipartFile file = workbookFile(new Object[][]{
                {"2026-03", "增值税", "日常/预缴", "-200.00", "已缴纳", LocalDate.of(2026, 3, 15), "退税"},
                {"2026-Q2", "企业所得税", "年度汇算清缴", "0.00", "待缴纳", null, ""},
                {"2026-Annual", "印花税", "", "1500.00", "免征/零申报", null, "年度归档"},
        }, headers());

        Result<?> result = TaxImportExcelHelper.importExcel(file, taxRecordMapper);

        assertEquals(200, result.getCode());
        assertEquals("成功导入 3 条税务记录", result.getMessage());

        ArgumentCaptor<TaxRecord> captor = ArgumentCaptor.forClass(TaxRecord.class);
        verify(taxRecordMapper, times(3)).insert(captor.capture());
        List<TaxRecord> inserted = captor.getAllValues();
        assertEquals(0, inserted.get(0).getTaxAmount().compareTo(new BigDecimal("-200.00")));
        assertEquals(0, inserted.get(1).getTaxAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, inserted.get(2).getTaxAmount().compareTo(new BigDecimal("1500.00")));
    }

    @Test
    void importExcelShouldRejectInvalidTaxPeriod() throws Exception {
        MockMultipartFile file = workbookFile(new Object[][]{
                {"2026-13", "增值税", "日常/预缴", "100.00", "待缴纳", null, ""},
        }, headers());

        Result<?> result = TaxImportExcelHelper.importExcel(file, taxRecordMapper);

        assertEquals(400, result.getCode());
        List<Map<String, Object>> errors = errorList(result);
        assertEquals(1, errors.size());
        assertEquals(2, errors.get(0).get("row"));
        assertTrue(String.valueOf(errors.get(0).get("error")).contains("税款所属期格式不正确"));
        verify(taxRecordMapper, never()).insert(any());
    }

    @Test
    void importExcelShouldRejectPaidRowWithoutPaymentDate() throws Exception {
        MockMultipartFile file = workbookFile(new Object[][]{
                {"2026-03", "增值税", "", "100.00", "已缴纳", null, ""},
        }, headers());

        Result<?> result = TaxImportExcelHelper.importExcel(file, taxRecordMapper);

        assertEquals(400, result.getCode());
        assertTrue(String.valueOf(errorList(result).get(0).get("error")).contains("已缴纳状态必须填写缴纳日期"));
        verify(taxRecordMapper, never()).insert(any());
    }

    @Test
    void importExcelShouldRejectPaymentDateForUnpaidStatuses() throws Exception {
        MockMultipartFile file = workbookFile(new Object[][]{
                {"2026-Q1", "企业所得税", "", "100.00", "待缴纳", LocalDate.of(2026, 4, 1), ""},
        }, headers());

        Result<?> result = TaxImportExcelHelper.importExcel(file, taxRecordMapper);

        assertEquals(400, result.getCode());
        assertTrue(String.valueOf(errorList(result).get(0).get("error")).contains("不能填写缴纳日期"));
        verify(taxRecordMapper, never()).insert(any());
    }

    @Test
    void importExcelShouldRejectDuplicateRowsInsideFile() throws Exception {
        MockMultipartFile file = workbookFile(new Object[][]{
                {"2026-Q2", "增值税", "", "100.00", "待缴纳", null, ""},
                {"2026-Q2", "增值税", "", "200.00", "待缴纳", null, ""},
        }, headers());

        Result<?> result = TaxImportExcelHelper.importExcel(file, taxRecordMapper);

        assertEquals(400, result.getCode());
        assertTrue(String.valueOf(errorList(result).get(0).get("error")).contains("所属期+税种重复"));
        verify(taxRecordMapper, never()).insert(any());
    }

    @Test
    void importExcelShouldRejectDuplicateRowsFromDatabase() throws Exception {
        when(taxRecordMapper.selectCount(any())).thenReturn(1L);

        MockMultipartFile file = workbookFile(new Object[][]{
                {"2026-Q2", "增值税", "", "100.00", "待缴纳", null, ""},
        }, headers());

        Result<?> result = TaxImportExcelHelper.importExcel(file, taxRecordMapper);

        assertEquals(400, result.getCode());
        assertTrue(String.valueOf(errorList(result).get(0).get("error")).contains("系统中已存在"));
        verify(taxRecordMapper, never()).insert(any());
    }

    @Test
    void importExcelShouldRejectMissingRequiredHeaders() throws Exception {
        MockMultipartFile file = workbookFile(new Object[][]{
                {"2026-Q2", "增值税", "100.00", "待缴纳", null, ""},
        }, new String[]{
                TaxImportTemplateSupport.TAX_PERIOD_COLUMN.header(),
                TaxImportTemplateSupport.TAX_TYPE_COLUMN.header(),
                TaxImportTemplateSupport.TAX_AMOUNT_COLUMN.header(),
                TaxImportTemplateSupport.PAYMENT_STATUS_COLUMN.header(),
                TaxImportTemplateSupport.PAYMENT_DATE_COLUMN.header(),
                TaxImportTemplateSupport.REMARK_COLUMN.header(),
        });

        Result<?> result = TaxImportExcelHelper.importExcel(file, taxRecordMapper);

        assertEquals(400, result.getCode());
        assertTrue(String.valueOf(errorList(result).get(0).get("error")).contains("缺少必需表头"));
        verify(taxRecordMapper, never()).insert(any());
    }

    private String[] headers() {
        return TaxImportTemplateSupport.IMPORT_COLUMNS.stream()
                .map(TaxImportTemplateSupport.ImportColumn::header)
                .toArray(String[]::new);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> errorList(Result<?> result) {
        assertInstanceOf(List.class, result.getData());
        return (List<Map<String, Object>>) result.getData();
    }

    private MockMultipartFile workbookFile(Object[][] rows, String[] headers) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.createSheet(TaxImportTemplateSupport.GUIDE_SHEET_NAME);
            Sheet sheet = workbook.createSheet(TaxImportTemplateSupport.DATA_SHEET_NAME);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            CreationHelper creationHelper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("yyyy-mm-dd"));

            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                Object[] values = rows[rowIndex];
                for (int columnIndex = 0; columnIndex < values.length; columnIndex++) {
                    Object value = values[columnIndex];
                    if (value == null) {
                        continue;
                    }
                    var cell = row.createCell(columnIndex);
                    if (value instanceof LocalDate localDate) {
                        cell.setCellValue(java.sql.Date.valueOf(localDate));
                        cell.setCellStyle(dateStyle);
                    } else {
                        cell.setCellValue(String.valueOf(value));
                    }
                }
            }

            workbook.write(outputStream);
            return new MockMultipartFile(
                    "file",
                    "tax-import.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    outputStream.toByteArray()
            );
        }
    }
}