package com.pjh.server.service.impl;

import com.pjh.server.audit.AuditOperationService;
import com.pjh.server.dashboard.HomeAiSummarySnapshotInvalidationPublisher;
import com.pjh.server.dto.FinanceCreateDTO;
import com.pjh.server.entity.FinanceRecord;
import com.pjh.server.mapper.FinanceRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceServiceImplTest {

    @Mock
    private FinanceRecordMapper financeRecordMapper;

    @Mock
    private AuditOperationService auditOperationService;

    @Mock
    private HomeAiSummarySnapshotInvalidationPublisher homeAiSummarySnapshotInvalidationPublisher;

    @InjectMocks
    private FinanceServiceImpl financeService;

    @Test
    void downloadTemplateShouldWriteExcelContent() throws Exception {
        FinanceServiceImpl service = new FinanceServiceImpl(null, null, null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.downloadTemplate(response);

        assertEquals(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                response.getContentType()
        );
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void updateRecordShouldNormalizeOptionalBlankStringsToNull() {
        FinanceRecord existing = new FinanceRecord();
        existing.setId(1L);
        when(financeRecordMapper.selectById(1L)).thenReturn(existing);

        FinanceCreateDTO dto = new FinanceCreateDTO();
        dto.setType(" expense ");
        dto.setAmount(new BigDecimal("1000.00"));
        dto.setCategory(" 采购支出 ");
        dto.setProject("   ");
        dto.setDate(LocalDate.of(2026, 4, 11));
        dto.setRemark("");

        financeService.updateRecord(1L, dto);

        ArgumentCaptor<FinanceRecord> captor = ArgumentCaptor.forClass(FinanceRecord.class);
        verify(financeRecordMapper).updateById(captor.capture());
        assertEquals("expense", captor.getValue().getType());
        assertEquals("采购支出", captor.getValue().getCategory());
        assertNull(captor.getValue().getProject());
        assertNull(captor.getValue().getRemark());
        verify(homeAiSummarySnapshotInvalidationPublisher).publishCurrentCompany();
    }

    @Test
    void createRecordShouldPublishCreateAuditWithNormalizedPayload() {
        when(financeRecordMapper.insert(any(FinanceRecord.class))).thenAnswer(invocation -> {
            FinanceRecord record = invocation.getArgument(0);
            record.setId(9L);
            return 1;
        });

        FinanceCreateDTO dto = new FinanceCreateDTO();
        dto.setType("income");
        dto.setAmount(new BigDecimal("5000.00"));
        dto.setCategory("销售收入");
        dto.setProject("  ");
        dto.setDate(LocalDate.of(2026, 4, 11));
        dto.setRemark("   ");

        financeService.createRecord(dto);

        ArgumentCaptor<FinanceRecord> captor = ArgumentCaptor.forClass(FinanceRecord.class);
        verify(financeRecordMapper).insert(captor.capture());
        assertNull(captor.getValue().getProject());
        assertNull(captor.getValue().getRemark());
        verify(auditOperationService).publishCreate(eq("finance"), eq(9L), any(FinanceRecord.class), any());
        verify(homeAiSummarySnapshotInvalidationPublisher).publishCurrentCompany();
    }
}
