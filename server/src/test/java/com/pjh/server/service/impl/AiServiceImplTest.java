package com.pjh.server.service.impl;

import com.pjh.server.ai.AiPromptBuilder;
import com.pjh.server.ai.AiToolFacade;
import com.pjh.server.config.AiProperties;
import com.pjh.server.dto.AiChatRequestDTO;
import com.pjh.server.mapper.CompanyMapper;
import com.pjh.server.service.AiHistoryService;
import com.pjh.server.service.AiHitlService;
import com.pjh.server.util.CurrentSessionService;
import dev.ai4j.openai4j.OpenAiHttpException;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceImplTest {

    @Mock
    private CurrentSessionService currentSessionService;

    @Mock
    private CompanyMapper companyMapper;

    @Mock
    private AiHistoryService aiHistoryService;

    @Mock
    private AiHitlService aiHitlService;

    @Mock
    private AiPromptBuilder aiPromptBuilder;

    @Mock
    private AiToolFacade aiToolFacade;

    @Mock
    private AiProperties aiProperties;

    @Mock
    private ObjectProvider<OpenAiChatModel> chatModelProvider;

    @Mock
    private Executor aiChatTaskExecutor;

    private AiServiceImpl aiService;

    @BeforeEach
    void setUp() {
        aiService = new AiServiceImpl(
                currentSessionService,
                companyMapper,
                aiHistoryService,
                aiHitlService,
                aiPromptBuilder,
                aiToolFacade,
                aiProperties,
                chatModelProvider,
                aiChatTaskExecutor
        );
    }

    @Test
    void streamChatShouldCaptureSessionContextBeforeAsyncExecution() {
        AiChatRequestDTO dto = new AiChatRequestDTO();
        dto.setMessage("hello");

        when(currentSessionService.requireCurrentCompanyId()).thenReturn(9L);
        when(currentSessionService.requireCurrentUserId()).thenReturn(7L);
        when(aiProperties.isEnabled()).thenReturn(true);
        when(aiProperties.getApiKey()).thenReturn("test-key");
        when(aiProperties.getBaseUrl()).thenReturn("https://example.com");
        when(aiProperties.getModel()).thenReturn("qwen3.5-flash");
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(aiChatTaskExecutor).execute(any(Runnable.class));

        aiService.streamChat(dto, new NoopSseEmitter());

        InOrder inOrder = inOrder(currentSessionService, aiChatTaskExecutor);
        inOrder.verify(currentSessionService).requireCurrentCompanyId();
        inOrder.verify(currentSessionService).requireCurrentUserId();
        inOrder.verify(aiChatTaskExecutor).execute(any(Runnable.class));

        verify(currentSessionService, times(1)).requireCurrentCompanyId();
        verify(currentSessionService, times(1)).requireCurrentUserId();
        verify(companyMapper).selectById(9L);
    }

    @Test
    void buildErrorPayloadShouldMapAuthenticationErrors() {
        AiServiceImpl.AiErrorPayload payload = aiService.buildErrorPayload(new OpenAiHttpException(401, "bad key"));

        assertThat(payload.code()).isEqualTo(401);
        assertThat(payload.message()).isEqualTo("API Key \u65e0\u6548\u6216\u65e0\u6a21\u578b\u6743\u9650");
    }

    @Test
    void buildErrorPayloadShouldMapModelNotFoundErrors() {
        AiServiceImpl.AiErrorPayload payload = aiService.buildErrorPayload(
                new CompletionException(new OpenAiHttpException(404, "model not found"))
        );

        assertThat(payload.code()).isEqualTo(404);
        assertThat(payload.message()).isEqualTo("\u6a21\u578b\u4e0d\u5b58\u5728\u6216\u63a5\u53e3\u5730\u5740\u9519\u8bef");
    }

    @Test
    void buildErrorPayloadShouldMapTimeoutErrors() {
        AiServiceImpl.AiErrorPayload payload = aiService.buildErrorPayload(
                new RuntimeException(new HttpTimeoutException("request timeout"))
        );

        assertThat(payload.code()).isEqualTo(504);
        assertThat(payload.message()).isEqualTo("AI \u670d\u52a1\u9650\u6d41\u6216\u8d85\u65f6");
    }

    @Test
    void buildErrorPayloadShouldFallbackForUnexpectedErrors() {
        AiServiceImpl.AiErrorPayload payload = aiService.buildErrorPayload(new RuntimeException("boom"));

        assertThat(payload.code()).isEqualTo(500);
        assertThat(payload.message()).isEqualTo("AI \u670d\u52a1\u6682\u65f6\u4e0d\u53ef\u7528");
    }

    @Test
    void deleteSessionShouldUseCurrentTenantScope() {
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(5L);
        when(currentSessionService.requireCurrentUserId()).thenReturn(8L);

        aiService.deleteSession("session-9");

        verify(aiHistoryService).deleteSession(5L, 8L, "session-9");
        verifyNoMoreInteractions(aiHistoryService);
    }

    private static class NoopSseEmitter extends SseEmitter {

        @Override
        public synchronized void send(SseEventBuilder builder) {
            // no-op for unit tests
        }
    }
}
