package com.pjh.server.vo;

import com.pjh.server.ai.AiActionRequiredPayload;

public record AiChatStreamActionRequiredVO(String sessionId, AiActionRequiredPayload actionRequired) {
}
