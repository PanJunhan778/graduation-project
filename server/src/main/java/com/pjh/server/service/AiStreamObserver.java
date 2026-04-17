package com.pjh.server.service;

import com.pjh.server.ai.AiActionRequiredPayload;
import com.pjh.server.vo.AiChatStreamDoneVO;

public interface AiStreamObserver {

    void onStart(String sessionId);

    void onToken(String delta);

    void onActionRequired(String sessionId, AiActionRequiredPayload payload);

    void onError(int code, String message);

    void onDone(AiChatStreamDoneVO payload);

    boolean isClosed();
}
