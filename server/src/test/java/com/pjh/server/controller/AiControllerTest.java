package com.pjh.server.controller;

import com.pjh.server.dto.AiConfirmActionDTO;
import com.pjh.server.service.AiService;
import com.pjh.server.vo.AiChatMessageVO;
import com.pjh.server.vo.AiConfirmActionVO;
import com.pjh.server.vo.AiSessionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiControllerTest {

    @Mock
    private AiService aiService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AiController(aiService))
                .build();
    }

    @Test
    void listSessionsShouldReturnWrappedResult() throws Exception {
        AiSessionVO session = new AiSessionVO();
        session.setSessionId("session-1");
        session.setTitle("分析本月支出");
        session.setLastMessagePreview("先看支出结构");
        session.setLastMessageTime(LocalDateTime.of(2026, 4, 12, 10, 30));

        when(aiService.listSessions()).thenReturn(List.of(session));

        mockMvc.perform(get("/api/ai/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].sessionId").value("session-1"))
                .andExpect(jsonPath("$.data[0].title").value("分析本月支出"));

        verify(aiService).listSessions();
    }

    @Test
    void listMessagesShouldReturnWrappedMessages() throws Exception {
        AiChatMessageVO message = new AiChatMessageVO();
        message.setId(1L);
        message.setRole("assistant");
        message.setMessageType("markdown");
        message.setContent("本月支出偏高");

        when(aiService.listMessages("session-1")).thenReturn(List.of(message));

        mockMvc.perform(get("/api/ai/sessions/session-1/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].messageType").value("markdown"))
                .andExpect(jsonPath("$.data[0].content").value("本月支出偏高"));

        verify(aiService).listMessages("session-1");
    }

    @Test
    void confirmActionShouldReturnWrappedResult() throws Exception {
        AiConfirmActionVO result = new AiConfirmActionVO();
        result.setActionId(9L);
        result.setStatus("approved");
        result.setResultMessage("已同意更新企业档案");

        when(aiService.confirmAction(any(AiConfirmActionDTO.class))).thenReturn(result);

        mockMvc.perform(post("/api/ai/confirm-action")
                        .contentType("application/json")
                        .content("""
                                {
                                  "confirmToken": "token-1",
                                  "isApproved": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.actionId").value(9))
                .andExpect(jsonPath("$.data.status").value("approved"));

        verify(aiService).confirmAction(any(AiConfirmActionDTO.class));
    }
}
