package com.pjh.server.controller;

import com.pjh.server.dto.AiChatRequestDTO;
import com.pjh.server.dto.AiConfirmActionDTO;
import com.pjh.server.service.AiService;
import com.pjh.server.service.AiStreamObserver;
import com.pjh.server.util.CurrentSessionService;
import com.pjh.server.util.TenantContextHolder;
import com.pjh.server.vo.AiChatMessageVO;
import com.pjh.server.vo.AiChatStreamDoneVO;
import com.pjh.server.vo.AiConfirmActionVO;
import com.pjh.server.vo.AiSessionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiControllerTest {

    @Mock
    private AiService aiService;

    @Mock
    private CurrentSessionService currentSessionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Executor directExecutor = Runnable::run;
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AiController(aiService, currentSessionService, directExecutor))
                .build();
    }

    @Test
    void chatShouldStreamSseEvents() throws Exception {
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(9L);
        when(currentSessionService.requireCurrentUserId()).thenReturn(7L);
        doAnswer(invocation -> {
            assertThat(TenantContextHolder.getCompanyId()).isEqualTo(9L);
            AiStreamObserver observer = invocation.getArgument(3, AiStreamObserver.class);
            observer.onStart("session-1");
            observer.onToken("hello");
            observer.onDone(AiChatStreamDoneVO.message("session-1", 123L, "markdown"));
            return null;
        }).when(aiService).chatStream(any(Long.class), any(Long.class), any(AiChatRequestDTO.class), any(AiStreamObserver.class));

        MvcResult mvcResult = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-1",
                                  "message": "hello"
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:start")))
                .andExpect(content().string(containsString("event:token")))
                .andExpect(content().string(containsString("event:done")))
                .andExpect(content().string(containsString("\"sessionId\":\"session-1\"")))
                .andExpect(content().string(containsString("\"messageId\":123")));

        assertThat(TenantContextHolder.getCompanyId()).isNull();
        verify(aiService).chatStream(any(Long.class), any(Long.class), any(AiChatRequestDTO.class), any(AiStreamObserver.class));
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
                        .contentType(MediaType.APPLICATION_JSON)
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

    @Test
    void deleteSessionShouldDelegateToService() throws Exception {
        mockMvc.perform(delete("/api/ai/sessions/session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(aiService).deleteSession("session-1");
    }
}
