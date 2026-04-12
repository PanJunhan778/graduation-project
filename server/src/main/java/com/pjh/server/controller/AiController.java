package com.pjh.server.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.pjh.server.common.Result;
import com.pjh.server.dto.AiChatRequestDTO;
import com.pjh.server.dto.AiConfirmActionDTO;
import com.pjh.server.service.AiService;
import com.pjh.server.vo.AiChatMessageVO;
import com.pjh.server.vo.AiChatTurnVO;
import com.pjh.server.vo.AiConfirmActionVO;
import com.pjh.server.vo.AiSessionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@SaCheckRole("owner")
public class AiController {

    private final AiService aiService;

    @PostMapping("/chat")
    public Result<AiChatTurnVO> chat(@RequestBody @Valid AiChatRequestDTO dto) {
        return Result.success(aiService.chat(dto));
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
}
