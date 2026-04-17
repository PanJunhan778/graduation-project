package com.pjh.server.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AuditOperationVO {

    private Long id;

    private String module;

    private String operationType;

    private Long targetId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operationTime;

    private Long userId;

    private String operatorName;

    private int changeCount;

    private List<AuditFieldChangeVO> changes;
}
