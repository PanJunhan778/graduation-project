package com.pjh.server.service.impl;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaxTemplateWorkbookHelperTest {

    @Test
    void downloadTemplateShouldWriteWorkbookWithGuideAndDataSheets() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        TaxTemplateWorkbookHelper.downloadTemplate(response);

        assertEquals(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                response.getContentType()
        );
        assertTrue(response.getContentAsByteArray().length > 0);

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertNotNull(workbook.getSheet(TaxImportTemplateSupport.GUIDE_SHEET_NAME));
            assertNotNull(workbook.getSheet(TaxImportTemplateSupport.DATA_SHEET_NAME));
            assertEquals(
                    TaxImportTemplateSupport.TAX_PERIOD_COLUMN.header(),
                    workbook.getSheet(TaxImportTemplateSupport.DATA_SHEET_NAME).getRow(0).getCell(0).getStringCellValue()
            );
        }
    }
}
