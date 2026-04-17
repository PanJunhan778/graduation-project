package com.pjh.server.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.pjh.server.service.AiStreamObserver;
import com.pjh.server.util.CurrentSessionService;
import com.pjh.server.vo.AiChatStreamDoneVO;
import dev.ai4j.openai4j.OpenAiHttpException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
    private ObjectProvider<OpenAiStreamingChatModel> streamingChatModelProvider;

    @Mock
    private OpenAiStreamingChatModel streamingChatModel;

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
                streamingChatModelProvider,
                new ObjectMapper()
        );
    }

    @Test
    void chatStreamShouldEmitStartTokenDoneAndPersistAssistantMessage() {
        stubCommonChatFlow();
        doAnswer(invocation -> {
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(2);
            handler.onNext("你");
            handler.onNext("好");
            handler.onComplete(Response.from(AiMessage.from("你好")));
            return null;
        }).when(streamingChatModel).generate(any(List.class), any(List.class), any(StreamingResponseHandler.class));

        RecordingObserver observer = new RecordingObserver();
        aiService.chatStream(9L, 7L, buildRequest(), observer);

        assertThat(observer.events).containsExactly("start", "token:你", "token:好", "done:message");
        assertThat(observer.startSessionId).isEqualTo("session-1");
        assertThat(observer.donePayload).isEqualTo(
                AiChatStreamDoneVO.message("session-1", 102L, AiConstants.MESSAGE_TYPE_MARKDOWN)
        );
        verify(companyMapper).selectById(9L);
        verify(aiHistoryService).createMessage(
                9L,
                7L,
                "session-1",
                "assistant",
                AiConstants.MESSAGE_TYPE_MARKDOWN,
                "你好",
                null
        );
    }

    @Test
    void chatStreamShouldContinueAfterToolExecutionAndEmitFinalDone() {
        stubCommonChatFlow();

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("tool-1")
                .name("calculate_financial_sum")
                .arguments("{\"type\":\"expense\"}")
                .build();

        AtomicInteger round = new AtomicInteger();
        doAnswer(invocation -> {
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(2);
            if (round.getAndIncrement() == 0) {
                handler.onComplete(Response.from(AiMessage.from(List.of(request))));
            } else {
                handler.onNext("done");
                handler.onComplete(Response.from(AiMessage.from("处理完成")));
            }
            return null;
        }).when(streamingChatModel).generate(any(List.class), any(List.class), any(StreamingResponseHandler.class));

        when(aiToolFacade.execute(9L, request)).thenReturn(AiToolExecutionOutcome.result("{\"total\":123.45}"));

        RecordingObserver observer = new RecordingObserver();
        aiService.chatStream(9L, 7L, buildRequest(), observer);

        assertThat(observer.events).containsExactly("start", "token:done", "done:message");
        assertThat(observer.donePayload).isEqualTo(
                AiChatStreamDoneVO.message("session-1", 102L, AiConstants.MESSAGE_TYPE_MARKDOWN)
        );
        verify(aiToolFacade).execute(9L, request);
        verify(aiHistoryService).createMessage(
                9L,
                7L,
                "session-1",
                "assistant",
                AiConstants.MESSAGE_TYPE_MARKDOWN,
                "处理完成",
                null
        );
    }

    @Test
    void chatStreamShouldEmitActionRequiredWithoutPersistingAssistantMessage() {
        stubCommonChatFlow();

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("tool-2")
                .name(AiConstants.ACTION_TYPE_UPDATE_COMPANY_DESCRIPTION)
                .arguments("{\"newDescription\":\"新业务\"}")
                .build();

        doAnswer(invocation -> {
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
        when(aiHitlService.createCompanyDescriptionPendingAction(9L, 7L, "session-1", "旧业务", "新业务"))
                .thenReturn(payload);

        RecordingObserver observer = new RecordingObserver();
        aiService.chatStream(9L, 7L, buildRequest(), observer);

        assertThat(observer.events).containsExactly("start", "action_required", "done:action_required");
        assertThat(observer.actionRequiredPayload).isSameAs(payload);
        assertThat(observer.donePayload).isEqualTo(AiChatStreamDoneVO.actionRequired("session-1"));
        verify(aiHistoryService, never()).createMessage(
                eq(9L),
                eq(7L),
                eq("session-1"),
                eq("assistant"),
                eq(AiConstants.MESSAGE_TYPE_MARKDOWN),
                any(),
                eq(null)
        );
    }

    @Test
    void chatStreamShouldEmitErrorAndDoneWhenModelFails() {
        stubCommonChatFlow();
        doAnswer(invocation -> {
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(2);
            handler.onError(new OpenAiHttpException(401, "bad key"));
            return null;
        }).when(streamingChatModel).generate(any(List.class), any(List.class), any(StreamingResponseHandler.class));

        RecordingObserver observer = new RecordingObserver();
        aiService.chatStream(9L, 7L, buildRequest(), observer);

        assertThat(observer.events).containsExactly("start", "error:401", "done:error");
        assertThat(observer.errorCode).isEqualTo(401);
        assertThat(observer.errorMessage).isEqualTo("API Key 无效或无模型权限");
        assertThat(observer.donePayload).isEqualTo(AiChatStreamDoneVO.error("session-1"));
        verify(aiHistoryService, never()).createMessage(
                eq(9L),
                eq(7L),
                eq("session-1"),
                eq("assistant"),
                eq(AiConstants.MESSAGE_TYPE_MARKDOWN),
                any(),
                eq(null)
        );
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
    void buildErrorPayloadShouldExplainToolCompatibilityErrors() {
        AiServiceImpl.AiErrorPayload payload = aiService.buildErrorPayload(
                new ClassCastException("class java.util.ImmutableCollections$List12 cannot be cast to class [Ljava.lang.Object;")
        );

        assertThat(payload.code()).isEqualTo(500);
        assertThat(payload.message()).isEqualTo("AI 工具参数配置不兼容，请检查工具定义");
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

        when(aiProperties.isEnabled()).thenReturn(true);
        when(aiProperties.getApiKey()).thenReturn("test-key");
        when(aiProperties.getBaseUrl()).thenReturn("https://example.com");
        when(aiProperties.getModel()).thenReturn("qwen3.5-flash");
        when(streamingChatModelProvider.getIfAvailable()).thenReturn(streamingChatModel);
        when(streamingChatModel.modelName()).thenReturn("qwen3.5-flash");
        when(companyMapper.selectById(9L)).thenReturn(company);
        when(aiHistoryService.loadRecentConversation(9L, 7L, "session-1", AiConstants.CHAT_MEMORY_WINDOW_ROUNDS))
                .thenReturn(List.of());
        when(aiPromptBuilder.buildChatMessages(company, Constants.ROLE_OWNER, List.of(), "hello"))
                .thenReturn(List.<ChatMessage>of());
        when(aiToolFacade.toolSpecifications()).thenReturn(List.of());
        when(aiHistoryService.createMessage(9L, 7L, "session-1", "user", AiConstants.MESSAGE_TYPE_TEXT, "hello", null))
                .thenReturn(userLog);
        lenient().when(aiHistoryService.createMessage(9L, 7L, "session-1", "assistant", AiConstants.MESSAGE_TYPE_MARKDOWN, "你好", null))
                .thenReturn(assistantLog);
        lenient().when(aiHistoryService.createMessage(9L, 7L, "session-1", "assistant", AiConstants.MESSAGE_TYPE_MARKDOWN, "处理完成", null))
                .thenReturn(assistantLog);
    }

    private AiChatRequestDTO buildRequest() {
        AiChatRequestDTO dto = new AiChatRequestDTO();
        dto.setSessionId("session-1");
        dto.setMessage("hello");
        return dto;
    }

    private static final class RecordingObserver implements AiStreamObserver {

        private final List<String> events = new ArrayList<>();
        private String startSessionId;
        private AiActionRequiredPayload actionRequiredPayload;
        private Integer errorCode;
        private String errorMessage;
        private AiChatStreamDoneVO donePayload;

        @Override
        public void onStart(String sessionId) {
            startSessionId = sessionId;
            events.add("start");
        }

        @Override
        public void onToken(String delta) {
            events.add("token:" + delta);
        }

        @Override
        public void onActionRequired(String sessionId, AiActionRequiredPayload payload) {
            actionRequiredPayload = payload;
            events.add("action_required");
        }

        @Override
        public void onError(int code, String message) {
            errorCode = code;
            errorMessage = message;
            events.add("error:" + code);
        }

        @Override
        public void onDone(AiChatStreamDoneVO payload) {
            donePayload = payload;
            events.add("done:" + payload.reason());
        }

        @Override
        public boolean isClosed() {
            return false;
        }
    }
}
