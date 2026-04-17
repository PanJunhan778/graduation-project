package com.pjh.server.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditOperationService {

    private final AuditDiffBuilder auditDiffBuilder;
    private final AuditLogEventPublisher auditLogEventPublisher;

    public void publishCreate(String module, Long targetId, Object current, String[] fields) {
        auditLogEventPublisher.publish(
                module,
                "CREATE",
                targetId,
                auditDiffBuilder.buildCreateChanges(current, fields)
        );
    }

    public void publishUpdate(String module, Long targetId, Object before, Object after, String[] fields) {
        auditLogEventPublisher.publish(
                module,
                "UPDATE",
                targetId,
                auditDiffBuilder.buildChanges(before, after, fields)
        );
    }

    public void publishDelete(String module, Long targetId, Object current, String[] fields) {
        auditLogEventPublisher.publish(
                module,
                "DELETE",
                targetId,
                auditDiffBuilder.buildDeleteChanges(current, fields)
        );
    }

    public void publishRestore(String module, Long targetId, Object current, String[] fields) {
        auditLogEventPublisher.publish(
                module,
                "RESTORE",
                targetId,
                auditDiffBuilder.buildCreateChanges(current, fields)
        );
    }
}
