package com.pjh.server.audit;

public record AuditLogPayload(
        Long companyId,
        Long userId,
        String module,
        String operationType,
        Long targetId,
        String fieldName,
        String oldValue,
        String newValue,
        String remark
) {
}
