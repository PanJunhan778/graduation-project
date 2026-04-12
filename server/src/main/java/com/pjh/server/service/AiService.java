package com.pjh.server.service;

import com.pjh.server.dto.AiChatRequestDTO;
import com.pjh.server.dto.AiConfirmActionDTO;
import com.pjh.server.vo.AiChatMessageVO;
import com.pjh.server.vo.AiConfirmActionVO;
import com.pjh.server.vo.AiSessionVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface AiService {

    void streamChat(AiChatRequestDTO dto, SseEmitter emitter);

    AiConfirmActionVO confirmAction(AiConfirmActionDTO dto);

    List<AiSessionVO> listSessions();

    List<AiChatMessageVO> listMessages(String sessionId);
}
