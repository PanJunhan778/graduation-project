package com.pjh.server.audit;

import com.pjh.server.util.CurrentSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogEventPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final CurrentSessionService currentSessionService;

    public void publish(String module, String operationType, Long targetId, List<AuditFieldChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }

        Long companyId = currentSessionService.requireCurrentCompanyId();
        Long userId = currentSessionService.requireCurrentUserId();
        List<String> changedFields = changes.stream()
                .map(AuditFieldChange::fieldName)
                .toList();

        log.debug(
                "Publishing audit event: module={}, operationType={}, targetId={}, changedFields={}",
                module,
                operationType,
                targetId,
                changedFields
        );

        List<AuditLogPayload> payloads = changes.stream()
                .map(change -> new AuditLogPayload(
                        companyId,
                        userId,
                        module,
                        operationType,
                        targetId,
                        change.fieldName(),
                        change.oldValue(),
                        change.newValue(),
                        null
                ))
                .toList();
        eventPublisher.publishEvent(new AuditLogEvent(payloads));
    }
}
