package com.pjh.server.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.pjh.server.service.AiStreamObserver;
import com.pjh.server.util.CurrentSessionService;
import com.pjh.server.vo.AiChatMessageVO;
import com.pjh.server.vo.AiChatStreamDoneVO;
import com.pjh.server.vo.AiConfirmActionVO;
import com.pjh.server.vo.AiSessionVO;
import dev.ai4j.openai4j.OpenAiHttpException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

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
    private final ObjectProvider<OpenAiStreamingChatModel> streamingChatModelProvider;
    private final ObjectMapper objectMapper;

    @Override
    public void chatStream(Long companyId, Long userId, AiChatRequestDTO dto, AiStreamObserver observer) {
        AiChatContext context = new AiChatContext(
                companyId,
                userId,
                normalizeSessionId(dto.getSessionId())
        );

        try {
            validateAiAvailability();
            log.info(
                    "Starting AI chat: sessionId={}, companyId={}, userId={}, model={}",
                    context.sessionId(),
                    context.companyId(),
                    context.userId(),
                    aiProperties.getModel()
            );

            String sessionId = context.sessionId();

            Company company = companyMapper.selectById(companyId);
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

            List<ChatMessage> messages = aiPromptBuilder.buildChatMessages(
                    company,
                    Constants.ROLE_OWNER,
                    history,
                    dto.getMessage()
            );

            if (observer.isClosed()) {
                return;
            }
            observer.onStart(sessionId);
            executeChatLoopStream(messages, company, companyId, userId, sessionId, observer);
        } catch (Exception e) {
            logChatFailure(context, "chatStream", e);
            emitStreamError(context.sessionId(), observer, e);
        }
    }

    private void executeChatLoopStream(List<ChatMessage> initialMessages,
                                       Company company,
                                       Long companyId,
                                       Long userId,
                                       String sessionId,
                                       AiStreamObserver observer) throws InterruptedException {
        OpenAiStreamingChatModel chatModel = streamingChatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new BusinessException("AI 模型未初始化，请先检查 app.ai 配置");
        }

        List<ChatMessage> messages = new ArrayList<>(initialMessages);
        List<ToolSpecification> toolSpecifications = aiToolFacade.toolSpecifications();

        for (int round = 0; round < AiConstants.CHAT_MAX_TOOL_ROUNDS; round++) {
            if (observer.isClosed()) {
                return;
            }

            int roundNumber = round + 1;
            StreamingRoundResult roundResult = invokeStreamingChatModelRound(
                    chatModel,
                    messages,
                    toolSpecifications,
                    sessionId,
                    companyId,
                    userId,
                    roundNumber,
                    observer
            );
            if (observer.isClosed()) {
                return;
            }

            AiMessage aiMessage = roundResult.response().content();
            if (aiMessage == null) {
                throw new BusinessException("AI 没有返回有效内容，请稍后重试");
            }

            if (aiMessage.hasToolExecutionRequests()) {
                messages.add(aiMessage);
                log.info(
                        "AI requested tool execution: sessionId={}, round={}, toolCount={}",
                        sessionId,
                        roundNumber,
                        aiMessage.toolExecutionRequests().size()
                );

                for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                    if (observer.isClosed()) {
                        return;
                    }

                    AiToolExecutionOutcome outcome;
                    try {
                        log.debug(
                                "Executing AI tool: sessionId={}, companyId={}, toolName={}, arguments={}",
                                sessionId,
                                companyId,
                                request.name(),
                                request.arguments()
                        );
                        outcome = aiToolFacade.execute(companyId, request);
                        ToolResultSummary resultSummary = summarizeToolResult(outcome.resultJson());
                        log.debug(
                                "AI tool completed: sessionId={}, companyId={}, toolName={}, arguments={}, resultCount={}, minDate={}, maxDate={}",
                                sessionId,
                                companyId,
                                request.name(),
                                request.arguments(),
                                resultSummary.resultCount(),
                                resultSummary.minDate(),
                                resultSummary.maxDate()
                        );
                    } catch (Exception e) {
                        log.error(
                                "AI tool execution failed: sessionId={}, companyId={}, userId={}, toolName={}, arguments={}",
                                sessionId,
                                companyId,
                                userId,
                                request.name(),
                                request.arguments(),
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
                        log.info(
                                "AI chat entered HITL flow: sessionId={}, actionId={}",
                                sessionId,
                                actionRequired.getActionId()
                        );
                        observer.onActionRequired(sessionId, actionRequired);
                        if (!observer.isClosed()) {
                            observer.onDone(AiChatStreamDoneVO.actionRequired(sessionId));
                        }
                        return;
                    }

                    messages.add(ToolExecutionResultMessage.from(request, outcome.resultJson()));
                }
                continue;
            }

            if (observer.isClosed()) {
                return;
            }

            String finalText = normalizeAssistantText(resolveAssistantText(aiMessage, roundResult.text()));
            var assistantLog = aiHistoryService.createMessage(
                    companyId,
                    userId,
                    sessionId,
                    "assistant",
                    AiConstants.MESSAGE_TYPE_MARKDOWN,
                    finalText,
                    null
            );
            log.debug(
                    "Stored assistant AI message: sessionId={}, messageId={}, length={}",
                    sessionId,
                    assistantLog.getId(),
                    finalText.length()
            );
            log.info(
                    "AI model response ready: sessionId={}, round={}, responseLength={}",
                    sessionId,
                    roundNumber,
                    finalText.length()
            );
            observer.onDone(AiChatStreamDoneVO.message(
                    sessionId,
                    assistantLog.getId(),
                    AiConstants.MESSAGE_TYPE_MARKDOWN
            ));
            return;
        }

        throw new BusinessException("AI 推理轮次超限，请换个问法后重试");
    }

    private StreamingRoundResult invokeStreamingChatModelRound(OpenAiStreamingChatModel chatModel,
                                                               List<ChatMessage> messages,
                                                               List<ToolSpecification> toolSpecifications,
                                                               String sessionId,
                                                               Long companyId,
                                                               Long userId,
                                                               int roundNumber,
                                                               AiStreamObserver observer) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Response<AiMessage>> responseRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        StringBuilder streamedText = new StringBuilder();

        try {
            log.debug(
                    "Invoking AI model: sessionId={}, round={}, model={}, messageCount={}, toolCount={}",
                    sessionId,
                    roundNumber,
                    chatModel.modelName(),
                    messages.size(),
                    toolSpecifications.size()
            );
            chatModel.generate(messages, toolSpecifications, new StreamingResponseHandler<>() {
                @Override
                public void onNext(String token) {
                    if (token == null || token.isEmpty()) {
                        return;
                    }
                    streamedText.append(token);
                    if (!observer.isClosed()) {
                        observer.onToken(token);
                    }
                }

                @Override
                public void onComplete(Response<AiMessage> response) {
                    responseRef.set(response);
                    latch.countDown();
                }

                @Override
                public void onError(Throwable throwable) {
                    errorRef.set(throwable);
                    latch.countDown();
                }
            });
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            log.error(
                    "AI model invocation failed: sessionId={}, companyId={}, userId={}, round={}, model={}",
                    sessionId,
                    companyId,
                    userId,
                    roundNumber,
                    aiProperties.getModel(),
                    e
            );
            throw e;
        }

        Throwable error = errorRef.get();
        if (error != null) {
            if (error instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(error);
        }

        Response<AiMessage> response = responseRef.get();
        if (response == null) {
            throw new BusinessException("AI 没有返回有效内容，请稍后重试");
        }
        return new StreamingRoundResult(response, streamedText.toString());
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
    }

    private String normalizeAssistantText(String text) {
        return text == null || text.isBlank()
                ? "抱歉，这次没有生成有效回答，请换个问法再试。"
                : text;
    }

    private String resolveAssistantText(AiMessage aiMessage, String streamedText) {
        if (aiMessage != null && aiMessage.text() != null && !aiMessage.text().isBlank()) {
            return aiMessage.text();
        }
        return streamedText;
    }

    private void emitStreamError(String sessionId, AiStreamObserver observer, Exception exception) {
        if (observer.isClosed()) {
            return;
        }
        AiErrorPayload payload = buildErrorPayload(exception);
        observer.onError(payload.code(), payload.message());
        if (!observer.isClosed()) {
            observer.onDone(AiChatStreamDoneVO.error(sessionId));
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
                case 401, 403 -> "API Key 无效或无模型权限";
                case 404 -> "模型不存在或接口地址错误";
                case 429 -> "AI 服务限流或超时";
                default -> "AI 服务暂时不可用";
            };
            return new AiErrorPayload(code, message);
        }

        if (rootCause instanceof IllegalArgumentException illegalArgumentException
                && illegalArgumentException.getMessage() != null
                && illegalArgumentException.getMessage().contains("unknown to jtokkit")) {
            return new AiErrorPayload(500, "AI 模型分词配置不兼容，请检查模型配置");
        }

        if (rootCause instanceof ClassCastException classCastException
                && classCastException.getMessage() != null
                && (classCastException.getMessage().contains("ImmutableCollections")
                || classCastException.getMessage().contains("[Ljava.lang.Object;"))) {
            return new AiErrorPayload(500, "AI 工具参数配置不兼容，请检查工具定义");
        }

        if (isTimeout(rootCause)) {
            return new AiErrorPayload(504, "AI 服务限流或超时");
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

    private ToolResultSummary summarizeToolResult(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return new ToolResultSummary("n/a", null, null);
        }

        try {
            JsonNode root = objectMapper.readTree(resultJson);
            String resultCount = extractResultCount(root);
            String minDate = readOptionalText(root, "minDate");
            String maxDate = readOptionalText(root, "maxDate");
            return new ToolResultSummary(resultCount, minDate, maxDate);
        } catch (Exception e) {
            return new ToolResultSummary("unparseable", null, null);
        }
    }

    private String extractResultCount(JsonNode root) {
        if (root == null || root.isNull()) {
            return "0";
        }
        if (root.hasNonNull("recordCount")) {
            return root.get("recordCount").asText();
        }
        if (root.hasNonNull("count")) {
            return root.get("count").asText();
        }
        if (root.has("records") && root.get("records").isArray()) {
            return String.valueOf(root.get("records").size());
        }
        if (root.isArray()) {
            return String.valueOf(root.size());
        }
        return "unknown";
    }

    private String readOptionalText(JsonNode root, String fieldName) {
        if (root == null || !root.hasNonNull(fieldName)) {
            return null;
        }
        return root.get(fieldName).asText();
    }

    record AiChatContext(Long companyId, Long userId, String sessionId) {
    }

    record AiErrorPayload(int code, String message) {
    }

    private record ToolResultSummary(String resultCount, String minDate, String maxDate) {
    }

    private record StreamingRoundResult(Response<AiMessage> response, String text) {
    }
}
