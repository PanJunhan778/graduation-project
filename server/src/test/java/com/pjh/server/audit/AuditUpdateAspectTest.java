package com.pjh.server.audit;

import com.pjh.server.entity.FinanceRecord;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditUpdateAspectTest {

    @Mock
    private AuditSnapshotProvider snapshotProvider;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private AuditOperationService auditOperationService;

    @Test
    void aroundUpdateShouldDelegateUsingSnapshotsWhenSharedEntityIsMutated() throws Throwable {
        AuditUpdateAspect aspect = new AuditUpdateAspect(List.of(snapshotProvider), new AuditDiffBuilder(), auditOperationService);
        FinanceRecord sharedRecord = financeRecord("old remark");

        when(snapshotProvider.module()).thenReturn("finance");
        when(snapshotProvider.loadById(9L)).thenReturn(sharedRecord, sharedRecord);
        when(joinPoint.getArgs()).thenReturn(new Object[]{9L, new Object()});
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            sharedRecord.setRemark("new remark");
            return null;
        });

        aspect.aroundUpdate(joinPoint, auditUpdateAnnotation());

        verify(auditOperationService).publishUpdate(
                eq("finance"),
                eq(9L),
                argThat(before -> before instanceof Map<?, ?> beforeMap
                        && "old remark".equals(beforeMap.get("remark"))
                        && "expense".equals(beforeMap.get("type"))),
                argThat(after -> after instanceof Map<?, ?> afterMap
                        && "new remark".equals(afterMap.get("remark"))
                        && "expense".equals(afterMap.get("type"))),
                eq(new String[]{"type", "amount", "category", "project", "date", "remark"})
        );
    }

    @Test
    void aroundUpdateShouldNotPublishWhenBusinessMethodThrows() throws Throwable {
        AuditUpdateAspect aspect = new AuditUpdateAspect(List.of(snapshotProvider), new AuditDiffBuilder(), auditOperationService);

        when(snapshotProvider.module()).thenReturn("finance");
        when(snapshotProvider.loadById(9L)).thenReturn(financeRecord("old remark"));
        when(joinPoint.getArgs()).thenReturn(new Object[]{9L, new Object()});
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("update failed"));

        assertThrows(IllegalStateException.class, () -> aspect.aroundUpdate(joinPoint, auditUpdateAnnotation()));

        verify(auditOperationService, never()).publishUpdate(anyString(), anyLong(), any(), any(), any());
    }

    private AuditUpdate auditUpdateAnnotation() throws NoSuchMethodException {
        return AuditTarget.class.getMethod("update", Long.class, Object.class).getAnnotation(AuditUpdate.class);
    }

    private FinanceRecord financeRecord(String remark) {
        FinanceRecord record = new FinanceRecord();
        record.setType("expense");
        record.setAmount(new BigDecimal("1000.00"));
        record.setCategory("采购支出");
        record.setProject("供应链项目");
        record.setDate(LocalDate.of(2026, 4, 11));
        record.setRemark(remark);
        return record;
    }

    private static class AuditTarget {
        @AuditUpdate(module = "finance", fields = {"type", "amount", "category", "project", "date", "remark"})
        public void update(Long id, Object dto) {
        }
    }
}
