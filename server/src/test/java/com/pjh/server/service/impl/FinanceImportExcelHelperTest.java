package com.pjh.server.service.impl;

import com.pjh.server.common.Result;
import com.pjh.server.entity.FinanceRecord;
import com.pjh.server.mapper.FinanceRecordMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FinanceImportExcelHelperTest {

    @Mock
    private FinanceRecordMapper financeRecordMapper;

    @Test
    void importExcelShouldParseRichTemplateWorkbook() throws Exception {
        MockMultipartFile file = workbookFile(new Object[][]{
                {"收入", "5000.00", "销售收入", "A 客户项目", LocalDate.of(2026, 4, 1), "季度合同回款"},
        }, headers());

        Result<?> result = FinanceImportExcelHelper.importExcel(file, financeRecordMapper);

        assertEquals(200, result.getCode());
        assertEquals("成功导入 1 条记录", result.getMessage());

        ArgumentCaptor<FinanceRecord> captor = ArgumentCaptor.forClass(FinanceRecord.class);
        verify(financeRecordMapper, times(1)).insert(captor.capture());
        FinanceRecord record = captor.getValue();
        assertEquals("income", record.getType());
        assertEquals(0, record.getAmount().compareTo(new BigDecimal("5000.00")));
        assertEquals("销售收入", record.getCategory());
        assertEquals(LocalDate.of(2026, 4, 1), record.getDate());
    }

    @Test
    void importExcelShouldRejectMissingHeaders() throws Exception {
        MockMultipartFile file = workbookFile(new Object[][]{
                {"收入", "5000.00", "销售收入", LocalDate.of(2026, 4, 1), "季度合同回款"},
        }, new String[]{
                FinanceImportTemplateSupport.TYPE_COLUMN.header(),
                FinanceImportTemplateSupport.AMOUNT_COLUMN.header(),
                FinanceImportTemplateSupport.CATEGORY_COLUMN.header(),
                FinanceImportTemplateSupport.DATE_COLUMN.header(),
                FinanceImportTemplateSupport.REMARK_COLUMN.header(),
        });

        Result<?> result = FinanceImportExcelHelper.importExcel(file, financeRecordMapper);

        assertEquals(400, result.getCode());
        assertTrue(String.valueOf(errorList(result).get(0).get("error")).contains("缺少必需表头"));
        verify(financeRecordMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    private String[] headers() {
        return FinanceImportTemplateSupport.IMPORT_COLUMNS.stream()
                .map(FinanceImportTemplateSupport.ImportColumn::header)
                .toArray(String[]::new);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> errorList(Result<?> result) {
        assertInstanceOf(List.class, result.getData());
        return (List<Map<String, Object>>) result.getData();
    }

    private MockMultipartFile workbookFile(Object[][] rows, String[] headers) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.createSheet(FinanceImportTemplateSupport.GUIDE_SHEET_NAME);
            Sheet sheet = workbook.createSheet(FinanceImportTemplateSupport.DATA_SHEET_NAME);

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
                    "finance-import.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    outputStream.toByteArray()
            );
        }
    }
}
