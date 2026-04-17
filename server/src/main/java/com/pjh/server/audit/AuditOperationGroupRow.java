package com.pjh.server.audit;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditOperationGroupRow {

    private String module;

    private String operationType;

    private Long targetId;

    private Long userId;

    private LocalDateTime operationTime;

    private Long maxId;

    private Long changeCount;
}
