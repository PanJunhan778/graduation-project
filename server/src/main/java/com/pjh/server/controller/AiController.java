package com.pjh.server.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.pjh.server.ai.AiActionRequiredPayload;
import com.pjh.server.common.AiConstants;
import com.pjh.server.common.Result;
import com.pjh.server.dto.AiChatRequestDTO;
import com.pjh.server.dto.AiConfirmActionDTO;
import com.pjh.server.service.AiService;
import com.pjh.server.service.AiStreamObserver;
import com.pjh.server.util.CurrentSessionService;
import com.pjh.server.util.TenantContextHolder;
import com.pjh.server.vo.AiChatMessageVO;
import com.pjh.server.vo.AiChatStreamActionRequiredVO;
import com.pjh.server.vo.AiChatStreamDoneVO;
import com.pjh.server.vo.AiChatStreamErrorVO;
import com.pjh.server.vo.AiChatStreamStartVO;
import com.pjh.server.vo.AiChatStreamTokenVO;
import com.pjh.server.vo.AiConfirmActionVO;
import com.pjh.server.vo.AiSessionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@SaCheckRole("owner")
public class AiController {

    private final AiService aiService;
    private final CurrentSessionService currentSessionService;

    @Qualifier("aiChatStreamTaskExecutor")
    private final Executor aiChatStreamTaskExecutor;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody @Valid AiChatRequestDTO dto) {
        Long companyId = currentSessionService.requireCurrentCompanyId();
        Long userId = currentSessionService.requireCurrentUserId();
        SseEmitter emitter = new SseEmitter(0L);
        SseAiStreamObserver observer = new SseAiStreamObserver(emitter);

        emitter.onCompletion(observer::markClosed);
        emitter.onTimeout(() -> {
            observer.markClosed();
            emitter.complete();
        });
        emitter.onError(throwable -> observer.markClosed());

        aiChatStreamTaskExecutor.execute(() -> {
            try {
                TenantContextHolder.setCompanyId(companyId);
                aiService.chatStream(companyId, userId, dto, observer);
            } catch (Exception e) {
                log.error("AI chat stream dispatch failed", e);
                observer.onError(500, "AI 服务暂时不可用");
                observer.onDone(AiChatStreamDoneVO.error(null));
            } finally {
                TenantContextHolder.clear();
            }
        });

        return emitter;
    }

    @PostMapping("/confirm-action")
    public Result<AiConfirmActionVO> confirmAction(@RequestBody @Valid AiConfirmActionDTO dto) {
        return Result.success(aiService.confirmAction(dto));
    }

    @GetMapping("/sessions")
    public Result<List<AiSessionVO>> listSessions() {
        return Result.success(aiService.listSessions());
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<AiChatMessageVO>> listMessages(@PathVariable String sessionId) {
        return Result.success(aiService.listMessages(sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        aiService.deleteSession(sessionId);
        return Result.success();
    }

    private static final class SseAiStreamObserver implements AiStreamObserver {

        private final SseEmitter emitter;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private SseAiStreamObserver(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void onStart(String sessionId) {
            send(AiConstants.STREAM_EVENT_START, new AiChatStreamStartVO(sessionId));
        }

        @Override
        public void onToken(String delta) {
            send(AiConstants.STREAM_EVENT_TOKEN, new AiChatStreamTokenVO(delta));
        }

        @Override
        public void onActionRequired(String sessionId, AiActionRequiredPayload payload) {
            send(
                    AiConstants.STREAM_EVENT_ACTION_REQUIRED,
                    new AiChatStreamActionRequiredVO(sessionId, payload)
            );
        }

        @Override
        public void onError(int code, String message) {
            send(AiConstants.STREAM_EVENT_ERROR, new AiChatStreamErrorVO(code, message));
        }

        @Override
        public void onDone(AiChatStreamDoneVO payload) {
            send(AiConstants.STREAM_EVENT_DONE, payload);
            complete();
        }

        @Override
        public boolean isClosed() {
            return closed.get();
        }

        private void send(String eventName, Object payload) {
            if (isClosed()) {
                return;
            }
            try {
                emitter.send(
                        SseEmitter.event()
                                .name(eventName)
                                .data(payload, MediaType.APPLICATION_JSON)
                );
            } catch (IOException | IllegalStateException e) {
                markClosed();
            }
        }

        private void complete() {
            if (closed.compareAndSet(false, true)) {
                emitter.complete();
            }
        }

        private void markClosed() {
            closed.set(true);
        }
    }
}
