package com.pjh.server.audit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditOperationGroupQuery {

    private Long companyId;

    private String module;

    private String operationType;

    private LocalDateTime startDateTime;

    private LocalDateTime endExclusive;

    private long offset;

    private long size;
}
