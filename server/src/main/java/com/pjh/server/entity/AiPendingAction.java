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
@TableName("ai_pending_action")
public class AiPendingAction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long companyId;

    private Long userId;

    private String sessionId;

    private Long chatMessageId;

    private String actionType;

    private String confirmToken;

    private String oldValue;

    private String proposedValue;

    private String status;

    private LocalDateTime expiresAt;

    private Long processedBy;

    private LocalDateTime processedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer isDeleted;
}
