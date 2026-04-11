package com.pjh.server.audit;

import com.pjh.server.entity.AuditLog;
import com.pjh.server.mapper.AuditLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogEventListenerTest {

    @Mock
    private AuditLogMapper auditLogMapper;

    @Test
    void handleAuditLogEventShouldPersistEveryFieldChange() {
        AuditLogEventListener listener = new AuditLogEventListener(auditLogMapper);
        AuditLogEvent event = new AuditLogEvent(List.of(
                new AuditLogPayload(1L, 2L, "finance", "UPDATE", 10L, "remark", "旧值", "新值", null),
                new AuditLogPayload(1L, 2L, "employee", "UPDATE", 20L, "salary", "8000", "9000", null)
        ));

        listener.handleAuditLogEvent(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper, times(2)).insert(captor.capture());
        assertEquals("remark", captor.getAllValues().get(0).getFieldName());
        assertEquals("salary", captor.getAllValues().get(1).getFieldName());
        assertEquals(Long.valueOf(1L), captor.getAllValues().get(0).getCompanyId());
    }
}
