package com.pjh.server.audit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditOperationGroupKey {

    private String module;

    private String operationType;

    private Long targetId;

    private Long userId;

    private LocalDateTime operationTime;
}
