package com.pjh.server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long companyId;

    private Long userId;

    private String module;

    private String operationType;

    private Long targetId;

    private String fieldName;

    private String oldValue;

    private String newValue;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime operationTime;

    private String remark;

    @TableLogic
    private Integer isDeleted;
}
