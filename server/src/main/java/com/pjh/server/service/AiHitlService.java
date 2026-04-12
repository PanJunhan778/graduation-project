package com.pjh.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pjh.server.ai.AiActionRequiredPayload;
import com.pjh.server.common.AiConstants;
import com.pjh.server.entity.AiPendingAction;
import com.pjh.server.entity.Company;
import com.pjh.server.exception.BusinessException;
import com.pjh.server.mapper.AiPendingActionMapper;
import com.pjh.server.mapper.CompanyMapper;
import com.pjh.server.vo.AiConfirmActionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiHitlService {

    private final AiPendingActionMapper aiPendingActionMapper;
    private final AiHistoryService aiHistoryService;
    private final CompanyMapper companyMapper;
    private final Clock clock;

    public AiActionRequiredPayload createCompanyDescriptionPendingAction(Long companyId, Long userId, String sessionId,
                                                                         String oldValue, String proposedValue) {
        AiPendingAction action = new AiPendingAction();
        action.setCompanyId(companyId);
        action.setUserId(userId);
        action.setSessionId(sessionId);
        action.setActionType(AiConstants.ACTION_TYPE_UPDATE_COMPANY_DESCRIPTION);
        action.setConfirmToken(UUID.randomUUID().toString().replace("-", ""));
        action.setOldValue(oldValue);
        action.setProposedValue(proposedValue);
        action.setStatus(AiConstants.ACTION_STATUS_PENDING);
        action.setExpiresAt(LocalDateTime.now(clock).plusMinutes(AiConstants.HITL_TOKEN_TTL_MINUTES));
        aiPendingActionMapper.insert(action);

        Map<String, Object> metadata = buildActionMetadata(action);
        var log = aiHistoryService.createMessage(
                companyId,
                userId,
                sessionId,
                "assistant",
                AiConstants.MESSAGE_TYPE_ACTION_REQUIRED,
                "AI 请求更新企业档案",
                metadata
        );

        action.setChatMessageId(log.getId());
        aiPendingActionMapper.updateById(action);

        AiActionRequiredPayload payload = new AiActionRequiredPayload();
        payload.setActionId(action.getId());
        payload.setToolName(action.getActionType());
        payload.setOldValue(oldValue);
        payload.setProposedValue(proposedValue);
        payload.setConfirmToken(action.getConfirmToken());
        return payload;
    }

    public AiConfirmActionVO confirmAction(Long companyId, Long userId, String confirmToken, boolean approved) {
        AiPendingAction action = aiPendingActionMapper.selectOne(new LambdaQueryWrapper<AiPendingAction>()
                .eq(AiPendingAction::getCompanyId, companyId)
                .eq(AiPendingAction::getUserId, userId)
                .eq(AiPendingAction::getConfirmToken, confirmToken)
                .last("LIMIT 1"));

        if (action == null) {
            throw new BusinessException("待确认动作不存在或无权访问");
        }

        if (!AiConstants.ACTION_STATUS_PENDING.equals(action.getStatus())) {
            return buildConfirmResult(action.getId(), action.getStatus(), buildProcessedMessage(action.getStatus()));
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (action.getExpiresAt() != null && action.getExpiresAt().isBefore(now)) {
            action.setStatus(AiConstants.ACTION_STATUS_EXPIRED);
            action.setProcessedBy(userId);
            action.setProcessedAt(now);
            aiPendingActionMapper.updateById(action);
            refreshActionCard(action);
            aiHistoryService.createMessage(companyId, userId, action.getSessionId(), "assistant",
                    AiConstants.MESSAGE_TYPE_ACTION_RESULT, "本次 AI 更新确认已过期，请重新发起。", actionResultMetadata(action));
            return buildConfirmResult(action.getId(), action.getStatus(), "本次 AI 更新确认已过期，请重新发起。");
        }

        if (approved) {
            applyApprovedAction(action);
            action.setStatus(AiConstants.ACTION_STATUS_APPROVED);
        } else {
            action.setStatus(AiConstants.ACTION_STATUS_REJECTED);
        }
        action.setProcessedBy(userId);
        action.setProcessedAt(now);
        aiPendingActionMapper.updateById(action);
        refreshActionCard(action);

        String resultMessage = approved
                ? "已同意更新企业档案，新的企业画像已生效。"
                : "已拒绝本次企业档案更新，原有企业画像保持不变。";

        aiHistoryService.createMessage(
                companyId,
                userId,
                action.getSessionId(),
                "assistant",
                AiConstants.MESSAGE_TYPE_ACTION_RESULT,
                resultMessage,
                actionResultMetadata(action)
        );

        return buildConfirmResult(action.getId(), action.getStatus(), resultMessage);
    }

    private void applyApprovedAction(AiPendingAction action) {
        if (!AiConstants.ACTION_TYPE_UPDATE_COMPANY_DESCRIPTION.equals(action.getActionType())) {
            throw new BusinessException("不支持的 HITL 动作类型");
        }

        Company company = companyMapper.selectById(action.getCompanyId());
        if (company == null) {
            throw new BusinessException("目标企业不存在");
        }
        company.setDescription(action.getProposedValue());
        companyMapper.updateById(company);
    }

    private void refreshActionCard(AiPendingAction action) {
        if (action.getChatMessageId() == null) {
            return;
        }
        aiHistoryService.updateMessageMetadata(action.getChatMessageId(), buildActionMetadata(action));
    }

    private Map<String, Object> buildActionMetadata(AiPendingAction action) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("actionId", action.getId());
        metadata.put("toolName", action.getActionType());
        metadata.put("oldValue", action.getOldValue());
        metadata.put("proposedValue", action.getProposedValue());
        metadata.put("confirmToken", action.getConfirmToken());
        metadata.put("status", action.getStatus());
        metadata.put("expiresAt", action.getExpiresAt());
        metadata.put("processedAt", action.getProcessedAt());
        return metadata;
    }

    private Map<String, Object> actionResultMetadata(AiPendingAction action) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("actionId", action.getId());
        metadata.put("status", action.getStatus());
        return metadata;
    }

    private AiConfirmActionVO buildConfirmResult(Long actionId, String status, String resultMessage) {
        AiConfirmActionVO vo = new AiConfirmActionVO();
        vo.setActionId(actionId);
        vo.setStatus(status);
        vo.setResultMessage(resultMessage);
        return vo;
    }

    private String buildProcessedMessage(String status) {
        return switch (status) {
            case AiConstants.ACTION_STATUS_APPROVED -> "该动作已审批通过，无需重复处理。";
            case AiConstants.ACTION_STATUS_REJECTED -> "该动作已被拒绝，无需重复处理。";
            case AiConstants.ACTION_STATUS_EXPIRED -> "该动作已过期，请重新发起。";
            default -> "该动作已经处理完成。";
        };
    }
}
