package com.pjh.server.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditLogVO {

    private Long id;

    private String module;

    private String operationType;

    private Long targetId;

    private String fieldName;

    private String oldValue;

    private String newValue;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operationTime;

    private Long userId;

    private String operatorName;
}
