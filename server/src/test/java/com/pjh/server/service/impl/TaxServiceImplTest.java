package com.pjh.server.service.impl;

import com.pjh.server.audit.AuditOperationService;
import com.pjh.server.dto.TaxUpsertDTO;
import com.pjh.server.entity.TaxRecord;
import com.pjh.server.exception.BusinessException;
import com.pjh.server.mapper.TaxRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxServiceImplTest {

    @Mock
    private TaxRecordMapper taxRecordMapper;

    @Mock
    private AuditOperationService auditOperationService;

    @InjectMocks
    private TaxServiceImpl taxService;

    @Test
    void createRecordShouldClearPaymentDateWhenStatusIsNotPaid() {
        TaxUpsertDTO dto = createValidDto(new BigDecimal("-200.00"));
        dto.setPaymentStatus(0);
        dto.setPaymentDate(LocalDate.of(2026, 4, 10));

        taxService.createRecord(dto);

        ArgumentCaptor<TaxRecord> captor = ArgumentCaptor.forClass(TaxRecord.class);
        verify(taxRecordMapper).insert(captor.capture());
        assertNull(captor.getValue().getPaymentDate());
        assertEquals(0, captor.getValue().getTaxAmount().compareTo(new BigDecimal("-200.00")));
        verify(auditOperationService).publishCreate(eq("tax"), any(), any(TaxRecord.class), any());
    }

    @Test
    void createRecordShouldAllowNegativeZeroAndPositiveTaxAmounts() {
        for (String amount : new String[]{"-200.00", "0.00", "1500.00"}) {
            clearInvocations(taxRecordMapper, auditOperationService);
            TaxUpsertDTO dto = createValidDto(new BigDecimal(amount));

            taxService.createRecord(dto);

            ArgumentCaptor<TaxRecord> captor = ArgumentCaptor.forClass(TaxRecord.class);
            verify(taxRecordMapper).insert(captor.capture());
            assertEquals(0, captor.getValue().getTaxAmount().compareTo(new BigDecimal(amount)));
            verify(auditOperationService).publishCreate(eq("tax"), any(), any(TaxRecord.class), any());
        }
    }

    @Test
    void updateRecordShouldRequirePaymentDateWhenStatusIsPaid() {
        TaxRecord existing = new TaxRecord();
        existing.setId(1L);
        when(taxRecordMapper.selectById(1L)).thenReturn(existing);

        TaxUpsertDTO dto = createValidDto(new BigDecimal("1500.00"));
        dto.setPaymentStatus(1);
        dto.setPaymentDate(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> taxService.updateRecord(1L, dto));

        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("日期"));
        verify(taxRecordMapper, never()).updateById(any(TaxRecord.class));
    }

    @Test
    void updateRecordShouldClearPaymentDateWhenStatusIsExempt() {
        TaxRecord existing = new TaxRecord();
        existing.setId(1L);
        when(taxRecordMapper.selectById(1L)).thenReturn(existing);

        TaxUpsertDTO dto = createValidDto(new BigDecimal("0.00"));
        dto.setPaymentStatus(2);
        dto.setPaymentDate(LocalDate.of(2026, 4, 10));

        taxService.updateRecord(1L, dto);

        ArgumentCaptor<TaxRecord> captor = ArgumentCaptor.forClass(TaxRecord.class);
        verify(taxRecordMapper).updateById(captor.capture());
        assertNull(captor.getValue().getPaymentDate());
    }

    @Test
    void deleteRecordShouldPublishDeleteAudit() {
        TaxRecord existing = new TaxRecord();
        existing.setId(1L);
        when(taxRecordMapper.selectById(1L)).thenReturn(existing);

        taxService.deleteRecord(1L);

        verify(taxRecordMapper).deleteById(1L);
        verify(auditOperationService).publishDelete(eq("tax"), eq(1L), same(existing), any());
    }

    private TaxUpsertDTO createValidDto(BigDecimal taxAmount) {
        TaxUpsertDTO dto = new TaxUpsertDTO();
        dto.setTaxPeriod("2026-Q2");
        dto.setTaxType("企业所得税");
        dto.setDeclarationType("日常/预缴");
        dto.setTaxAmount(taxAmount);
        dto.setPaymentStatus(1);
        dto.setPaymentDate(LocalDate.of(2026, 4, 10));
        dto.setRemark("测试数据");
        return dto;
    }
}