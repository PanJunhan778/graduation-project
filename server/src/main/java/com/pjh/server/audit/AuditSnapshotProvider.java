package com.pjh.server.audit;

public interface AuditSnapshotProvider {

    String module();

    Object loadById(Long id);
}
