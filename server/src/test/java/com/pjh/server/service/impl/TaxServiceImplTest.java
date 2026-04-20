package com.pjh.server.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pjh.server.audit.AuditOperationService;
import com.pjh.server.audit.DeleteAuditMetadataResolver;
import com.pjh.server.dashboard.HomeAiSummarySnapshotInvalidationPublisher;
import com.pjh.server.dto.TaxUpsertDTO;
import com.pjh.server.entity.TaxRecord;
import com.pjh.server.exception.BusinessException;
import com.pjh.server.mapper.TaxRecordMapper;
import com.pjh.server.util.CurrentSessionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Mock
    private HomeAiSummarySnapshotInvalidationPublisher homeAiSummarySnapshotInvalidationPublisher;

    @Mock
    private DeleteAuditMetadataResolver deleteAuditMetadataResolver;

    @Mock
    private CurrentSessionService currentSessionService;

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
        verify(homeAiSummarySnapshotInvalidationPublisher).publishCurrentCompany();
    }

    @Test
    void createRecordShouldAllowNegativeZeroAndPositiveTaxAmounts() {
        for (String amount : new String[]{"-200.00", "0.00", "1500.00"}) {
            clearInvocations(taxRecordMapper, auditOperationService, homeAiSummarySnapshotInvalidationPublisher);
            TaxUpsertDTO dto = createValidDto(new BigDecimal(amount));

            taxService.createRecord(dto);

            ArgumentCaptor<TaxRecord> captor = ArgumentCaptor.forClass(TaxRecord.class);
            verify(taxRecordMapper).insert(captor.capture());
            assertEquals(0, captor.getValue().getTaxAmount().compareTo(new BigDecimal(amount)));
            verify(auditOperationService).publishCreate(eq("tax"), any(), any(TaxRecord.class), any());
            verify(homeAiSummarySnapshotInvalidationPublisher).publishCurrentCompany();
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
        verify(homeAiSummarySnapshotInvalidationPublisher).publishCurrentCompany();
    }

    @Test
    void deleteRecordShouldPublishDeleteAudit() {
        TaxRecord existing = new TaxRecord();
        existing.setId(1L);
        when(taxRecordMapper.selectById(1L)).thenReturn(existing);

        taxService.deleteRecord(1L);

        verify(taxRecordMapper).deleteById(1L);
        verify(auditOperationService).publishDelete(eq("tax"), eq(1L), same(existing), any());
        verify(homeAiSummarySnapshotInvalidationPublisher).publishCurrentCompany();
    }

    @Test
    void restoreRecordShouldPublishRestoreAudit() {
        TaxRecord deletedRecord = taxRecord(7L, LocalDateTime.of(2026, 4, 9, 9, 0));
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(88L);
        when(currentSessionService.requireCurrentUserId()).thenReturn(66L);
        when(taxRecordMapper.selectDeletedById(88L, 7L)).thenReturn(deletedRecord);
        when(taxRecordMapper.restoreDeletedById(88L, 7L, 66L)).thenReturn(1);

        taxService.restoreRecord(7L);

        verify(auditOperationService).publishRestore(eq("tax"), eq(7L), same(deletedRecord), any());
        verify(homeAiSummarySnapshotInvalidationPublisher).publishCurrentCompany();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listRecordsShouldBuildKeywordQueryAcrossMultipleFieldsAndStatus() {
        initTaxLambdaCache();
        when(taxRecordMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            Page<TaxRecord> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });

        taxService.listRecords(1, 20, "企业", 1);

        ArgumentCaptor<LambdaQueryWrapper<TaxRecord>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(taxRecordMapper).selectPage(any(Page.class), captor.capture());

        String sqlSegment = captor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("tax_period"));
        assertTrue(sqlSegment.contains("tax_type"));
        assertTrue(sqlSegment.contains("declaration_type"));
        assertTrue(sqlSegment.contains("remark"));
        assertTrue(sqlSegment.contains("payment_status"));
        assertEquals(5, captor.getValue().getParamNameValuePairs().size());
    }

    @Test
    void batchRestoreShouldRestoreOnlyMatchedDeletedRecords() {
        TaxRecord first = taxRecord(8L, LocalDateTime.of(2026, 4, 10, 9, 0));
        TaxRecord second = taxRecord(9L, LocalDateTime.of(2026, 4, 11, 9, 0));
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(88L);
        when(currentSessionService.requireCurrentUserId()).thenReturn(66L);
        when(taxRecordMapper.selectDeletedByIds(88L, List.of(8L, 9L, 999L))).thenReturn(List.of(first, second));
        when(taxRecordMapper.restoreDeletedBatch(88L, List.of(8L, 9L), 66L)).thenReturn(2);

        int restoredCount = taxService.batchRestore(List.of(8L, 9L, 999L));

        assertEquals(2, restoredCount);
        verify(auditOperationService).publishRestore(eq("tax"), eq(8L), same(first), any());
        verify(auditOperationService).publishRestore(eq("tax"), eq(9L), same(second), any());
        verify(homeAiSummarySnapshotInvalidationPublisher).publishCurrentCompany();
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

    private TaxRecord taxRecord(Long id, LocalDateTime updatedTime) {
        TaxRecord record = new TaxRecord();
        record.setId(id);
        record.setTaxPeriod("2026-Q2");
        record.setTaxType("企业所得税");
        record.setDeclarationType("日常/预缴");
        record.setTaxAmount(new BigDecimal("500.00"));
        record.setPaymentStatus(1);
        record.setPaymentDate(LocalDate.of(2026, 4, 10));
        record.setUpdatedBy(12L);
        record.setUpdatedTime(updatedTime);
        record.setCreatedTime(updatedTime.minusDays(3));
        return record;
    }

    private void initTaxLambdaCache() {
        LambdaUtils.installCache(
                TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TaxRecord.class)
        );
    }
}
