package com.pjh.server.vo;

import com.pjh.server.ai.AiActionRequiredPayload;
import lombok.Data;

@Data
public class AiChatTurnVO {

    private String sessionId;

    private String resultType;

    private Long messageId;

    private String messageType;

    private String content;

    private AiActionRequiredPayload actionRequired;
}
