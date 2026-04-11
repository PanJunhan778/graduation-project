package com.pjh.server.audit;

import java.util.List;

public record AuditLogEvent(List<AuditLogPayload> payloads) {
}
