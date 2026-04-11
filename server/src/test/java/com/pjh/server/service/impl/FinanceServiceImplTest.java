package com.pjh.server.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceServiceImplTest {

    @Test
    void downloadTemplateShouldWriteExcelContent() throws Exception {
        FinanceServiceImpl service = new FinanceServiceImpl(null, null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.downloadTemplate(response);

        assertEquals(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                response.getContentType()
        );
        assertTrue(response.getContentAsByteArray().length > 0);
    }
}