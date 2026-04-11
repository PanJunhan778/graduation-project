package com.pjh.server.audit;

import com.pjh.server.util.CurrentSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogEventPublisherTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private CurrentSessionService currentSessionService;

    @InjectMocks
    private AuditLogEventPublisher auditLogEventPublisher;

    @Test
    void publishShouldWrapFieldChangesIntoAuditEvent() {
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(88L);
        when(currentSessionService.requireCurrentUserId()).thenReturn(99L);

        auditLogEventPublisher.publish(
                "finance",
                "CREATE",
                123L,
                List.of(
                        new AuditFieldChange("amount", null, "5200"),
                        new AuditFieldChange("remark", null, "first record")
                )
        );

        ArgumentCaptor<AuditLogEvent> captor = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(2, captor.getValue().payloads().size());
        assertEquals("finance", captor.getValue().payloads().get(0).module());
        assertEquals("CREATE", captor.getValue().payloads().get(0).operationType());
        assertEquals(Long.valueOf(123L), captor.getValue().payloads().get(0).targetId());
        assertEquals("amount", captor.getValue().payloads().get(0).fieldName());
    }

    @Test
    void publishShouldSkipEmptyChanges() {
        auditLogEventPublisher.publish("finance", "UPDATE", 123L, List.of());

        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }
}
