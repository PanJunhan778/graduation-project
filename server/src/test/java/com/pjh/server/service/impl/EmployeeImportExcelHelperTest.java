package com.pjh.server.service.impl;

import com.pjh.server.common.Result;
import com.pjh.server.entity.Employee;
import com.pjh.server.mapper.EmployeeMapper;
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
class EmployeeImportExcelHelperTest {

    @Mock
    private EmployeeMapper employeeMapper;

    @Test
    void importExcelShouldParseRichTemplateWorkbookAndDefaultStatus() throws Exception {
        MockMultipartFile file = workbookFile(new Object[][]{
                {"张三", "市场部", "招商主管", "8000.00", LocalDate.of(2026, 4, 1), "", "试用期三个月"},
        }, headers());

        Result<?> result = EmployeeImportExcelHelper.importExcel(file, employeeMapper);

        assertEquals(200, result.getCode());
        assertEquals("成功导入 1 条员工记录", result.getMessage());

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeMapper, times(1)).insert(captor.capture());
        Employee employee = captor.getValue();
        assertEquals("张三", employee.getName());
        assertEquals("市场部", employee.getDepartment());
        assertEquals(0, employee.getSalary().compareTo(new BigDecimal("8000.00")));
        assertEquals(LocalDate.of(2026, 4, 1), employee.getHireDate());
        assertEquals(1, employee.getStatus());
    }

    @Test
    void importExcelShouldRejectMissingHeaders() throws Exception {
        MockMultipartFile file = workbookFile(new Object[][]{
                {"张三", "市场部", "8000.00", LocalDate.of(2026, 4, 1), "在职", "试用期三个月"},
        }, new String[]{
                EmployeeImportTemplateSupport.NAME_COLUMN.header(),
                EmployeeImportTemplateSupport.DEPARTMENT_COLUMN.header(),
                EmployeeImportTemplateSupport.SALARY_COLUMN.header(),
                EmployeeImportTemplateSupport.HIRE_DATE_COLUMN.header(),
                EmployeeImportTemplateSupport.STATUS_COLUMN.header(),
                EmployeeImportTemplateSupport.REMARK_COLUMN.header(),
        });

        Result<?> result = EmployeeImportExcelHelper.importExcel(file, employeeMapper);

        assertEquals(400, result.getCode());
        assertTrue(String.valueOf(errorList(result).get(0).get("error")).contains("缺少必需表头"));
        verify(employeeMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    private String[] headers() {
        return EmployeeImportTemplateSupport.IMPORT_COLUMNS.stream()
                .map(EmployeeImportTemplateSupport.ImportColumn::header)
                .toArray(String[]::new);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> errorList(Result<?> result) {
        assertInstanceOf(List.class, result.getData());
        return (List<Map<String, Object>>) result.getData();
    }

    private MockMultipartFile workbookFile(Object[][] rows, String[] headers) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.createSheet(EmployeeImportTemplateSupport.GUIDE_SHEET_NAME);
            Sheet sheet = workbook.createSheet(EmployeeImportTemplateSupport.DATA_SHEET_NAME);

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
                    "employee-import.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    outputStream.toByteArray()
            );
        }
    }
}
