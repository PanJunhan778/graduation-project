package com.pjh.server.audit;

public record AuditFieldChange(
        String fieldName,
        String oldValue,
        String newValue
) {
}
