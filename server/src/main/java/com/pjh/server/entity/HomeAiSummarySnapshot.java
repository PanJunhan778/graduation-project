package com.pjh.server.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("home_ai_summary_snapshot")
public class HomeAiSummarySnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long companyId;

    private String summaryLinesJson;

    private String status;

    private Integer isDirty;

    private LocalDateTime generatedAt;

    private LocalDateTime refreshStartedAt;

    private String lastError;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer isDeleted;
}
