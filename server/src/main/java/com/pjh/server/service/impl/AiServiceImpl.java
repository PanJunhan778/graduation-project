package com.pjh.server.service.impl;

import com.pjh.server.ai.AiActionRequiredPayload;
import com.pjh.server.ai.AiPromptBuilder;
import com.pjh.server.ai.AiToolExecutionOutcome;
import com.pjh.server.ai.AiToolFacade;
import com.pjh.server.common.AiConstants;
import com.pjh.server.common.Constants;
import com.pjh.server.config.AiProperties;
import com.pjh.server.dto.AiChatRequestDTO;
import com.pjh.server.dto.AiConfirmActionDTO;
import com.pjh.server.entity.Company;
import com.pjh.server.exception.BusinessException;
import com.pjh.server.mapper.CompanyMapper;
import com.pjh.server.service.AiHistoryService;
import com.pjh.server.service.AiHitlService;
import com.pjh.server.service.AiService;
import com.pjh.server.util.CurrentSessionService;
import com.pjh.server.vo.AiChatMessageVO;
import com.pjh.server.vo.AiConfirmActionVO;
import com.pjh.server.vo.AiSessionVO;
import dev.ai4j.openai4j.OpenAiHttpException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final CurrentSessionService currentSessionService;
    private final CompanyMapper companyMapper;
    private final AiHistoryService aiHistoryService;
    private final AiHitlService aiHitlService;
    private final AiPromptBuilder aiPromptBuilder;
    private final AiToolFacade aiToolFacade;
    private final AiProperties aiProperties;
    private final ObjectProvider<OpenAiChatModel> chatModelProvider;
    @Qualifier("aiChatTaskExecutor")
    private final Executor aiChatTaskExecutor;

    @Override
    public void streamChat(AiChatRequestDTO dto, SseEmitter emitter) {
        AiChatContext context = new AiChatContext(
                currentSessionService.requireCurrentCompanyId(),
                currentSessionService.requireCurrentUserId(),
                normalizeSessionId(dto.getSessionId())
        );

        CompletableFuture.runAsync(() -> doStreamChat(context, dto, emitter), aiChatTaskExecutor)
                .exceptionally(throwable -> {
                    Exception exception = toException(throwable);
                    logChatFailure(context, "async_dispatch", exception);
                    sendError(emitter, context, exception);
                    return null;
                });
    }

    @Override
    public AiConfirmActionVO confirmAction(AiConfirmActionDTO dto) {
        Long companyId = currentSessionService.requireCurrentCompanyId();
        Long userId = currentSessionService.requireCurrentUserId();
        return aiHitlService.confirmAction(companyId, userId, dto.getConfirmToken(), Boolean.TRUE.equals(dto.getIsApproved()));
    }

    @Override
    public List<AiSessionVO> listSessions() {
        Long companyId = currentSessionService.requireCurrentCompanyId();
        Long userId = currentSessionService.requireCurrentUserId();
        return aiHistoryService.listSessions(companyId, userId);
    }

    @Override
    public List<AiChatMessageVO> listMessages(String sessionId) {
        Long companyId = currentSessionService.requireCurrentCompanyId();
        Long userId = currentSessionService.requireCurrentUserId();
        return aiHistoryService.listMessages(companyId, userId, sessionId);
    }

    @Override
    public void deleteSession(String sessionId) {
        Long companyId = currentSessionService.requireCurrentCompanyId();
        Long userId = currentSessionService.requireCurrentUserId();
        aiHistoryService.deleteSession(companyId, userId, sessionId);
    }

    void doStreamChat(AiChatContext context, AiChatRequestDTO dto, SseEmitter emitter) {
        try {
            validateAiAvailability();
            log.info(
                    "Starting AI chat stream: sessionId={}, companyId={}, userId={}, model={}",
                    context.sessionId(),
                    context.companyId(),
                    context.userId(),
                    aiProperties.getModel()
            );

            sendEvent(emitter, "session", Map.of("sessionId", context.sessionId()));

            Long companyId = context.companyId();
            Long userId = context.userId();
            String sessionId = context.sessionId();

            Company company = companyMapper.selectById(context.companyId());
            if (company == null) {
                throw new BusinessException("当前企业不存在");
            }
            if (company == null) {
                throw new BusinessException("当前企业不存在");
            }

            List<ChatMessage> history = aiHistoryService.loadRecentConversation(
                    companyId,
                    userId,
                    sessionId,
                    AiConstants.CHAT_MEMORY_WINDOW_ROUNDS
            );
            log.debug("Loaded AI conversation history: sessionId={}, messageCount={}", sessionId, history.size());

            var userLog = aiHistoryService.createMessage(
                    companyId,
                    userId,
                    sessionId,
                    "user",
                    AiConstants.MESSAGE_TYPE_TEXT,
                    dto.getMessage(),
                    null
            );
            log.debug("Stored user AI message: sessionId={}, messageId={}", sessionId, userLog.getId());

            List<ChatMessage> messages = aiPromptBuilder.buildChatMessages(company, Constants.ROLE_OWNER, history, dto.getMessage());
            ChatExecutionResult executionResult = executeChatLoop(messages, company, companyId, userId, sessionId);

            if (executionResult.actionRequired() != null) {
                log.info("AI chat entered HITL flow: sessionId={}, actionId={}", sessionId, executionResult.actionRequired().getActionId());
                sendEvent(emitter, AiConstants.MESSAGE_TYPE_ACTION_REQUIRED, executionResult.actionRequired());
                emitter.complete();
                return;
            }

            String finalText = executionResult.finalText();
            finalText = finalText == null || finalText.isBlank()
                    ? "\u62b1\u6b49\uff0c\u8fd9\u6b21\u6ca1\u6709\u751f\u6210\u6709\u6548\u56de\u7b54\uff0c\u8bf7\u6362\u4e2a\u95ee\u6cd5\u518d\u8bd5\u3002"
                    : finalText;
            /*
            finalText = (finalText == null || finalText.isBlank())
                    ? "抱歉，这次没有生成有效回答，请换个问法再试。"
                    : finalText;
            */
            if (finalText == null || finalText.isBlank()) {
                finalText = "抱歉，这次没有生成有效回答，请换个问法再试。";
            }

            var assistantLog = aiHistoryService.createMessage(
                    companyId,
                    userId,
                    sessionId,
                    "assistant",
                    AiConstants.MESSAGE_TYPE_MARKDOWN,
                    finalText,
                    null
            );
            log.debug("Stored assistant AI message: sessionId={}, messageId={}, length={}", sessionId, assistantLog.getId(), finalText.length());

            try {
                for (String chunk : splitIntoChunks(finalText)) {
                    sendEvent(emitter, "token", Map.of("content", chunk));
                }
                sendEvent(emitter, "done", Map.of("messageId", assistantLog.getId(), "content", finalText));
            } catch (IOException e) {
                logChatFailure(context, "sse_send_final", e);
                throw e;
            }
            emitter.complete();
        } catch (Exception e) {
            logChatFailure(context, "stream_chat", e);
            sendError(emitter, context, e);
        }
    }

    private ChatExecutionResult executeChatLoop(List<ChatMessage> initialMessages, Company company,
                                                Long companyId, Long userId, String sessionId) {
        OpenAiChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new BusinessException("AI 模型未初始化，请先检查 app.ai 配置");
        }
        if (chatModel == null) {
            throw new BusinessException("AI 模型未初始化，请先填写 app.ai 配置");
        }

        List<ChatMessage> messages = new ArrayList<>(initialMessages);
        List<ToolSpecification> toolSpecifications = aiToolFacade.toolSpecifications();

        for (int round = 0; round < AiConstants.CHAT_MAX_TOOL_ROUNDS; round++) {
            Response<AiMessage> response;
            try {
                log.debug(
                        "Invoking AI model: sessionId={}, round={}, model={}, messageCount={}, toolCount={}",
                        sessionId,
                        round + 1,
                        chatModel.modelName(),
                        messages.size(),
                        toolSpecifications.size()
                );
                response = chatModel.generate(messages, toolSpecifications);
            } catch (Exception e) {
                log.error(
                        "AI model invocation failed: sessionId={}, companyId={}, userId={}, model={}",
                        sessionId,
                        companyId,
                        userId,
                        aiProperties.getModel(),
                        e
                );
                throw e;
            }
            AiMessage aiMessage = response.content();
            if (aiMessage == null) {
                throw new BusinessException("AI 没有返回有效内容，请稍后重试");
            }

            if (aiMessage.hasToolExecutionRequests()) {
                messages.add(aiMessage);
                log.info(
                        "AI requested tool execution: sessionId={}, round={}, toolCount={}",
                        sessionId,
                        round + 1,
                        aiMessage.toolExecutionRequests().size()
                );
                for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                    AiToolExecutionOutcome outcome;
                    try {
                        log.debug("Executing AI tool: sessionId={}, toolName={}", sessionId, request.name());
                        outcome = aiToolFacade.execute(companyId, request);
                    } catch (Exception e) {
                        log.error(
                                "AI tool execution failed: sessionId={}, companyId={}, userId={}, toolName={}",
                                sessionId,
                                companyId,
                                userId,
                                request.name(),
                                e
                        );
                        throw e;
                    }
                    if (outcome.requiresCompanyDescriptionUpdate()) {
                        String oldValue = company.getDescription() == null ? "" : company.getDescription();
                        AiActionRequiredPayload actionRequired = aiHitlService.createCompanyDescriptionPendingAction(
                                companyId,
                                userId,
                                sessionId,
                                oldValue,
                                outcome.pendingCompanyDescription()
                        );
                        return ChatExecutionResult.actionRequired(actionRequired);
                    }
                    messages.add(ToolExecutionResultMessage.from(request, outcome.resultJson()));
                }
                continue;
            }

            log.info(
                    "AI model response ready: sessionId={}, round={}, responseLength={}",
                    sessionId,
                    round + 1,
                    aiMessage.text() == null ? 0 : aiMessage.text().length()
            );
            return ChatExecutionResult.finalText(aiMessage.text());
        }

        throw new BusinessException("AI 推理轮次超限，请换个问法后重试");
    }

    private void validateAiAvailability() {
        if (!aiProperties.isEnabled()) {
            throw new BusinessException(500, "AI 服务尚未启用，请先在配置文件中开启 app.ai.enabled");
        }
        if (aiProperties.getApiKey() == null || aiProperties.getApiKey().isBlank()) {
            throw new BusinessException(500, "AI 服务缺少 API Key 配置");
        }
        if (aiProperties.getBaseUrl() == null || aiProperties.getBaseUrl().isBlank()) {
            throw new BusinessException(500, "AI 服务缺少 base-url 配置");
        }
        if (aiProperties.getModel() == null || aiProperties.getModel().isBlank()) {
            throw new BusinessException(500, "AI 服务缺少模型名称配置");
        }
        if (!aiProperties.isEnabled()) {
            throw new BusinessException(500, "AI 服务尚未启用，请先在配置文件中填写模型参数");
        }
        if (aiProperties.getApiKey() == null || aiProperties.getApiKey().isBlank()) {
            throw new BusinessException(500, "AI 服务缺少 API Key 配置");
        }
    }

    private List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        String content = text == null ? "" : text;
        int chunkSize = 18;
        for (int index = 0; index < content.length(); index += chunkSize) {
            chunks.add(content.substring(index, Math.min(content.length(), index + chunkSize)));
        }
        if (chunks.isEmpty()) {
            chunks.add("");
        }
        return chunks;
    }

    private void sendEvent(SseEmitter emitter, String event, Object payload) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(payload));
    }

    private void sendError(SseEmitter emitter, AiChatContext context, Exception exception) {
        if (context != null) {
            AiErrorPayload payload = buildErrorPayload(exception);
            Throwable rootCause = unwrap(exception);
            log.warn(
                    "Sending AI error event: sessionId={}, companyId={}, userId={}, model={}, code={}, message={}, rootCause={}",
                    context.sessionId(),
                    context.companyId(),
                    context.userId(),
                    aiProperties.getModel(),
                    payload.code(),
                    payload.message(),
                    rootCause.toString()
            );

            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("code", payload.code());
                body.put("message", payload.message());
                emitter.send(SseEmitter.event().name("error").data(body));
            } catch (Exception sendException) {
                log.error(
                        "Failed to send AI error event: sessionId={}, companyId={}, userId={}",
                        context.sessionId(),
                        context.companyId(),
                        context.userId(),
                        sendException
                );
            } finally {
                emitter.complete();
            }
            return;
        }
        try {
            String message = exception instanceof BusinessException businessException
                    ? businessException.getMessage()
                    : "AI 服务暂时不可用";
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("code", 500);
            payload.put("message", message);
            emitter.send(SseEmitter.event().name("error").data(payload));
        } catch (Exception ignored) {
        } finally {
            emitter.complete();
        }
    }

    AiErrorPayload buildErrorPayload(Exception exception) {
        Throwable rootCause = unwrap(exception);
        if (rootCause instanceof BusinessException businessException) {
            return new AiErrorPayload(businessException.getCode(), businessException.getMessage());
        }

        OpenAiHttpException httpException = findCause(rootCause, OpenAiHttpException.class);
        if (httpException != null) {
            int code = httpException.code();
            String message = switch (code) {
                case 401, 403 -> "API Key \u65e0\u6548\u6216\u65e0\u6a21\u578b\u6743\u9650";
                case 404 -> "\u6a21\u578b\u4e0d\u5b58\u5728\u6216\u63a5\u53e3\u5730\u5740\u9519\u8bef";
                case 429 -> "AI \u670d\u52a1\u9650\u6d41\u6216\u8d85\u65f6";
                default -> "AI \u670d\u52a1\u6682\u65f6\u4e0d\u53ef\u7528";
            };
            /*
            String message = switch (code) {
                case 401, 403 -> "API Key 无效或无模型权限";
                case 404 -> "模型不存在或接口地址错误";
                case 429 -> "AI 服务限流或超时";
                default -> "AI 服务暂时不可用";
            };
            */
            return new AiErrorPayload(code, message);
        }

        if (isTimeout(rootCause)) {
            return new AiErrorPayload(504, "AI \u670d\u52a1\u9650\u6d41\u6216\u8d85\u65f6");
        }

        if (isTimeout(rootCause)) {
            return new AiErrorPayload(504, "AI 服务限流或超时");
        }

        if (rootCause instanceof ConnectException || rootCause instanceof UnknownHostException) {
            return new AiErrorPayload(502, "AI \u670d\u52a1\u8fde\u63a5\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u63a5\u53e3\u5730\u5740\u6216\u7f51\u7edc");
        }

        if (rootCause != null) {
            return new AiErrorPayload(500, "AI \u670d\u52a1\u6682\u65f6\u4e0d\u53ef\u7528");
        }

        if (rootCause instanceof ConnectException || rootCause instanceof UnknownHostException) {
            return new AiErrorPayload(502, "AI 服务连接失败，请检查接口地址或网络");
        }

        return new AiErrorPayload(500, "AI 服务暂时不可用");
    }

    private void logChatFailure(AiChatContext context, String stage, Exception exception) {
        Throwable rootCause = unwrap(exception);
        log.error(
                "AI chat failed: stage={}, sessionId={}, companyId={}, userId={}, model={}, rootType={}, rootMessage={}",
                stage,
                context.sessionId(),
                context.companyId(),
                context.userId(),
                aiProperties.getModel(),
                rootCause.getClass().getName(),
                rootCause.getMessage(),
                exception
        );
    }

    private String normalizeSessionId(String sessionId) {
        return sessionId == null || sessionId.isBlank()
                ? UUID.randomUUID().toString()
                : sessionId.trim();
    }

    private Exception toException(Throwable throwable) {
        Throwable actual = unwrap(throwable);
        return actual instanceof Exception exception ? exception : new RuntimeException(actual);
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        Throwable root = current;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    private boolean isTimeout(Throwable throwable) {
        return throwable instanceof HttpTimeoutException
                || throwable instanceof SocketTimeoutException
                || throwable instanceof TimeoutException
                || (throwable.getMessage() != null && throwable.getMessage().toLowerCase().contains("timeout"));
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    record AiChatContext(Long companyId, Long userId, String sessionId) {
    }

    record AiErrorPayload(int code, String message) {
    }

    private record ChatExecutionResult(String finalText, AiActionRequiredPayload actionRequired) {

        static ChatExecutionResult finalText(String text) {
            return new ChatExecutionResult(text, null);
        }

        static ChatExecutionResult actionRequired(AiActionRequiredPayload payload) {
            return new ChatExecutionResult(null, payload);
        }
    }
}
