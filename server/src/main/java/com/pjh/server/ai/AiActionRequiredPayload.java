package com.pjh.server.ai;

import lombok.Data;

@Data
public class AiActionRequiredPayload {

    private Long actionId;

    private String toolName;

    private String oldValue;

    private String proposedValue;

    private String confirmToken;
}
