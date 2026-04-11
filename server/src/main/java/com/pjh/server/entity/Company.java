package com.pjh.server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("company")
public class Company {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String industry;

    private String companyCode;

    private String taxpayerType;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer isDeleted;
}
