package com.pjh.server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("tax_record")
public class TaxRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long companyId;

    private String taxPeriod;

    private String taxType;

    private String declarationType;

    private BigDecimal taxAmount;

    private Integer paymentStatus;

    private LocalDate paymentDate;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer isDeleted;
}
