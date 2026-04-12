package com.pjh.server.service;

import com.pjh.server.dto.AiChatRequestDTO;
import com.pjh.server.dto.AiConfirmActionDTO;
import com.pjh.server.vo.AiChatMessageVO;
import com.pjh.server.vo.AiChatTurnVO;
import com.pjh.server.vo.AiConfirmActionVO;
import com.pjh.server.vo.AiSessionVO;

import java.util.List;

public interface AiService {

    AiChatTurnVO chat(AiChatRequestDTO dto);

    AiConfirmActionVO confirmAction(AiConfirmActionDTO dto);

    List<AiSessionVO> listSessions();

    List<AiChatMessageVO> listMessages(String sessionId);

    void deleteSession(String sessionId);
}
