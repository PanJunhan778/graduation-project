package com.pjh.server.service.impl;

import com.pjh.server.ai.AiActionRequiredPayload;
import com.pjh.server.ai.AiPromptBuilder;
import com.pjh.server.ai.AiToolExecutionOutcome;
import com.pjh.server.ai.AiToolFacade;
import com.pjh.server.common.AiConstants;
import com.pjh.server.common.Constants;
import com.pjh.server.config.AiProperties;
import com.pjh.server.dto.AiChatRequestDTO;
import com.pjh.server.entity.AiChatLog;
import com.pjh.server.entity.Company;
import com.pjh.server.mapper.CompanyMapper;
import com.pjh.server.service.AiHistoryService;
import com.pjh.server.service.AiHitlService;
import com.pjh.server.util.CurrentSessionService;
import dev.ai4j.openai4j.OpenAiHttpException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
    private OpenAiChatModel chatModel;

    @Mock
    private ObjectProvider<OpenAiStreamingChatModel> streamingChatModelProvider;

    @Mock
    private OpenAiStreamingChatModel streamingChatModel;

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
                streamingChatModelProvider,
                aiChatTaskExecutor
        );
    }

    @Test
    void streamChatShouldCaptureSessionContextBeforeAsyncExecution() {
        stubCommonChatFlow();
        streamChatWithPlainResponse("好的", List.of("好", "的"), new NoopSseEmitter());

        InOrder inOrder = inOrder(currentSessionService, aiChatTaskExecutor);
        inOrder.verify(currentSessionService).requireCurrentCompanyId();
        inOrder.verify(currentSessionService).requireCurrentUserId();
        inOrder.verify(aiChatTaskExecutor).execute(any(Runnable.class));

        verify(currentSessionService, times(1)).requireCurrentCompanyId();
        verify(currentSessionService, times(1)).requireCurrentUserId();
        verify(companyMapper).selectById(9L);
    }

    @Test
    void streamChatShouldEmitThinkingTokensAndDoneForPlainResponse() {
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        stubCommonChatFlow();

        streamChatWithPlainResponse("你好", List.of("你", "好"), emitter);

        assertThat(emitter.eventNames()).containsExactly(
                AiConstants.SSE_EVENT_SESSION,
                AiConstants.SSE_EVENT_THINKING,
                AiConstants.SSE_EVENT_TOKEN,
                AiConstants.SSE_EVENT_TOKEN,
                AiConstants.SSE_EVENT_DONE
        );
        assertThat(emitter.payloads().get(1)).isEqualTo(Map.of("round", 1, "phase", AiConstants.THINKING_PHASE_MODEL_WAIT));
        assertThat(emitter.payloads().get(2)).isEqualTo(Map.of("content", "你"));
        assertThat(emitter.payloads().get(3)).isEqualTo(Map.of("content", "好"));
        assertThat(emitter.payloads().get(4)).isEqualTo(Map.of("messageId", 102L, "content", "你好"));
        verify(aiHistoryService).createMessage(9L, 7L, "session-1", "assistant", AiConstants.MESSAGE_TYPE_MARKDOWN, "你好", null);
    }

    @Test
    void streamChatShouldContinueStreamingAfterToolExecution() {
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        stubCommonChatFlow();

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("tool-1")
                .name("calculate_financial_sum")
                .arguments("{\"type\":\"expense\"}")
                .build();

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(2);
            handler.onComplete(Response.from(AiMessage.from(List.of(request))));
            return null;
        }).doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(2);
            handler.onNext("处理");
            handler.onNext("完成");
            handler.onComplete(Response.from(AiMessage.from("处理完成")));
            return null;
        }).when(streamingChatModel).generate(any(List.class), any(List.class), any(StreamingResponseHandler.class));

        when(aiToolFacade.execute(9L, request)).thenReturn(AiToolExecutionOutcome.result("{\"total\":123.45}"));

        aiService.streamChat(buildRequest(), emitter);

        assertThat(emitter.eventNames()).containsExactly(
                AiConstants.SSE_EVENT_SESSION,
                AiConstants.SSE_EVENT_THINKING,
                AiConstants.SSE_EVENT_THINKING,
                AiConstants.SSE_EVENT_TOKEN,
                AiConstants.SSE_EVENT_TOKEN,
                AiConstants.SSE_EVENT_DONE
        );
        verify(aiToolFacade).execute(9L, request);
        verify(aiHistoryService).createMessage(9L, 7L, "session-1", "assistant", AiConstants.MESSAGE_TYPE_MARKDOWN, "处理完成", null);
    }

    @Test
    void streamChatShouldEmitActionRequiredWithoutAssistantDoneMessage() {
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        stubCommonChatFlow();

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("tool-2")
                .name(AiConstants.ACTION_TYPE_UPDATE_COMPANY_DESCRIPTION)
                .arguments("{\"newDescription\":\"新业务\"}")
                .build();

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(2);
            handler.onComplete(Response.from(AiMessage.from(List.of(request))));
            return null;
        }).when(streamingChatModel).generate(any(List.class), any(List.class), any(StreamingResponseHandler.class));

        when(aiToolFacade.execute(9L, request)).thenReturn(AiToolExecutionOutcome.pendingUpdate("新业务"));

        AiActionRequiredPayload payload = new AiActionRequiredPayload();
        payload.setActionId(99L);
        payload.setToolName(AiConstants.ACTION_TYPE_UPDATE_COMPANY_DESCRIPTION);
        payload.setOldValue("旧业务");
        payload.setProposedValue("新业务");
        payload.setConfirmToken("token-1");
        when(aiHitlService.createCompanyDescriptionPendingAction(9L, 7L, "session-1", "旧业务", "新业务")).thenReturn(payload);

        aiService.streamChat(buildRequest(), emitter);

        assertThat(emitter.eventNames()).containsExactly(
                AiConstants.SSE_EVENT_SESSION,
                AiConstants.SSE_EVENT_THINKING,
                AiConstants.MESSAGE_TYPE_ACTION_REQUIRED
        );
        verify(aiHistoryService, never()).createMessage(eq(9L), eq(7L), eq("session-1"), eq("assistant"), eq(AiConstants.MESSAGE_TYPE_MARKDOWN), any(), eq(null));
    }

    @Test
    void buildErrorPayloadShouldMapAuthenticationErrors() {
        AiServiceImpl.AiErrorPayload payload = aiService.buildErrorPayload(new OpenAiHttpException(401, "bad key"));

        assertThat(payload.code()).isEqualTo(401);
        assertThat(payload.message()).isEqualTo("API Key 无效或无模型权限");
    }

    @Test
    void buildErrorPayloadShouldMapModelNotFoundErrors() {
        AiServiceImpl.AiErrorPayload payload = aiService.buildErrorPayload(
                new CompletionException(new OpenAiHttpException(404, "model not found"))
        );

        assertThat(payload.code()).isEqualTo(404);
        assertThat(payload.message()).isEqualTo("模型不存在或接口地址错误");
    }

    @Test
    void buildErrorPayloadShouldMapTimeoutErrors() {
        AiServiceImpl.AiErrorPayload payload = aiService.buildErrorPayload(
                new RuntimeException(new HttpTimeoutException("request timeout"))
        );

        assertThat(payload.code()).isEqualTo(504);
        assertThat(payload.message()).isEqualTo("AI 服务限流或超时");
    }

    @Test
    void buildErrorPayloadShouldExplainTokenizerCompatibilityErrors() {
        AiServiceImpl.AiErrorPayload payload = aiService.buildErrorPayload(
                new IllegalArgumentException("Model 'qwen3.5-flash' is unknown to jtokkit")
        );

        assertThat(payload.code()).isEqualTo(500);
        assertThat(payload.message()).isEqualTo("AI 模型分词配置不兼容，请检查模型配置");
    }

    @Test
    void buildErrorPayloadShouldFallbackForUnexpectedErrors() {
        AiServiceImpl.AiErrorPayload payload = aiService.buildErrorPayload(new RuntimeException("boom"));

        assertThat(payload.code()).isEqualTo(500);
        assertThat(payload.message()).isEqualTo("AI 服务暂时不可用");
    }

    @Test
    void deleteSessionShouldUseCurrentTenantScope() {
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(5L);
        when(currentSessionService.requireCurrentUserId()).thenReturn(8L);

        aiService.deleteSession("session-9");

        verify(aiHistoryService).deleteSession(5L, 8L, "session-9");
        verifyNoMoreInteractions(aiHistoryService);
    }

    private void stubCommonChatFlow() {
        Company company = new Company();
        company.setId(9L);
        company.setDescription("旧业务");

        AiChatLog userLog = new AiChatLog();
        userLog.setId(101L);
        AiChatLog assistantLog = new AiChatLog();
        assistantLog.setId(102L);

        when(currentSessionService.requireCurrentCompanyId()).thenReturn(9L);
        when(currentSessionService.requireCurrentUserId()).thenReturn(7L);
        when(aiProperties.isEnabled()).thenReturn(true);
        when(aiProperties.getApiKey()).thenReturn("test-key");
        when(aiProperties.getBaseUrl()).thenReturn("https://example.com");
        when(aiProperties.getModel()).thenReturn("qwen3.5-flash");
        when(streamingChatModelProvider.getIfAvailable()).thenReturn(streamingChatModel);
        when(streamingChatModel.modelName()).thenReturn("qwen3.5-flash");
        when(companyMapper.selectById(9L)).thenReturn(company);
        when(aiHistoryService.loadRecentConversation(9L, 7L, "session-1", AiConstants.CHAT_MEMORY_WINDOW_ROUNDS)).thenReturn(List.of());
        when(aiPromptBuilder.buildChatMessages(company, Constants.ROLE_OWNER, List.of(), "hello")).thenReturn(List.<ChatMessage>of());
        when(aiToolFacade.toolSpecifications()).thenReturn(List.of());
        when(aiHistoryService.createMessage(9L, 7L, "session-1", "user", AiConstants.MESSAGE_TYPE_TEXT, "hello", null)).thenReturn(userLog);
        lenient().when(aiHistoryService.createMessage(9L, 7L, "session-1", "assistant", AiConstants.MESSAGE_TYPE_MARKDOWN, "你好", null)).thenReturn(assistantLog);
        lenient().when(aiHistoryService.createMessage(9L, 7L, "session-1", "assistant", AiConstants.MESSAGE_TYPE_MARKDOWN, "好的", null)).thenReturn(assistantLog);
        lenient().when(aiHistoryService.createMessage(9L, 7L, "session-1", "assistant", AiConstants.MESSAGE_TYPE_MARKDOWN, "处理完成", null)).thenReturn(assistantLog);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(aiChatTaskExecutor).execute(any(Runnable.class));
    }

    private void streamChatWithPlainResponse(String finalText, List<String> tokens, SseEmitter emitter) {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(2);
            for (String token : tokens) {
                handler.onNext(token);
            }
            handler.onComplete(Response.from(AiMessage.from(finalText)));
            return null;
        }).when(streamingChatModel).generate(any(List.class), any(List.class), any(StreamingResponseHandler.class));

        aiService.streamChat(buildRequest(), emitter);
    }

    private AiChatRequestDTO buildRequest() {
        AiChatRequestDTO dto = new AiChatRequestDTO();
        dto.setSessionId("session-1");
        dto.setMessage("hello");
        return dto;
    }

    private static class NoopSseEmitter extends SseEmitter {

        @Override
        public synchronized void send(SseEventBuilder builder) {
            // no-op for unit tests
        }
    }

    private static class CapturingSseEmitter extends SseEmitter {

        private final List<String> eventNames = new ArrayList<>();
        private final List<Object> payloads = new ArrayList<>();

        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            String eventName = null;
            Object payload = null;
            for (ResponseBodyEmitter.DataWithMediaType dataWithMediaType : builder.build()) {
                Object data = dataWithMediaType.getData();
                if (data instanceof String text && text.startsWith("event:")) {
                    eventName = text.substring("event:".length()).split("\\R", 2)[0].trim();
                } else if (!(data instanceof String)) {
                    payload = data;
                }
            }
            eventNames.add(eventName);
            payloads.add(payload);
        }

        List<String> eventNames() {
            return eventNames;
        }

        List<Object> payloads() {
            return payloads;
        }
    }
}
