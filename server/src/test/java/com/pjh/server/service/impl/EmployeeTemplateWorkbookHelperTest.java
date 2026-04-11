package com.pjh.server.service.impl;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeTemplateWorkbookHelperTest {

    @Test
    void downloadTemplateShouldWriteWorkbookWithGuideAndDataSheets() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        EmployeeTemplateWorkbookHelper.downloadTemplate(response);

        assertEquals(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                response.getContentType()
        );
        assertTrue(response.getContentAsByteArray().length > 0);

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertNotNull(workbook.getSheet(EmployeeImportTemplateSupport.GUIDE_SHEET_NAME));
            assertNotNull(workbook.getSheet(EmployeeImportTemplateSupport.DATA_SHEET_NAME));
            assertEquals(
                    EmployeeImportTemplateSupport.NAME_COLUMN.header(),
                    workbook.getSheet(EmployeeImportTemplateSupport.DATA_SHEET_NAME).getRow(0).getCell(0).getStringCellValue()
            );
        }
    }
}
