package com.pjh.server.vo;

import com.pjh.server.common.AiConstants;

public record AiChatStreamDoneVO(
        String sessionId,
        String reason,
        Long messageId,
        String messageType
) {

    public static AiChatStreamDoneVO message(String sessionId, Long messageId, String messageType) {
        return new AiChatStreamDoneVO(sessionId, AiConstants.STREAM_DONE_REASON_MESSAGE, messageId, messageType);
    }

    public static AiChatStreamDoneVO actionRequired(String sessionId) {
        return new AiChatStreamDoneVO(sessionId, AiConstants.STREAM_DONE_REASON_ACTION_REQUIRED, null, null);
    }

    public static AiChatStreamDoneVO error(String sessionId) {
        return new AiChatStreamDoneVO(sessionId, AiConstants.STREAM_DONE_REASON_ERROR, null, null);
    }
}
