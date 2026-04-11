package com.pjh.server.audit;

import com.pjh.server.entity.AuditLog;
import com.pjh.server.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogEventListener {

    private final AuditLogMapper auditLogMapper;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAuditLogEvent(AuditLogEvent event) {
        for (AuditLogPayload payload : event.payloads()) {
            try {
                AuditLog auditLog = new AuditLog();
                auditLog.setCompanyId(payload.companyId());
                auditLog.setUserId(payload.userId());
                auditLog.setModule(payload.module());
                auditLog.setOperationType(payload.operationType());
                auditLog.setTargetId(payload.targetId());
                auditLog.setFieldName(payload.fieldName());
                auditLog.setOldValue(payload.oldValue());
                auditLog.setNewValue(payload.newValue());
                auditLog.setRemark(payload.remark());
                auditLogMapper.insert(auditLog);
            } catch (Exception ex) {
                log.error(
                        "Failed to persist audit log: module={}, operationType={}, targetId={}, fieldName={}",
                        payload.module(),
                        payload.operationType(),
                        payload.targetId(),
                        payload.fieldName(),
                        ex
                );
            }
        }
    }
}
