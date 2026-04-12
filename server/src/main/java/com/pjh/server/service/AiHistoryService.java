package com.pjh.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pjh.server.common.AiConstants;
import com.pjh.server.entity.AiChatLog;
import com.pjh.server.exception.BusinessException;
import com.pjh.server.mapper.AiChatLogMapper;
import com.pjh.server.vo.AiChatMessageVO;
import com.pjh.server.vo.AiSessionVO;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiHistoryService {

    private final AiChatLogMapper aiChatLogMapper;
    private final ObjectMapper objectMapper;

    public AiChatLog createMessage(Long companyId, Long userId, String sessionId, String role,
                                   String messageType, String content, Map<String, Object> metadata) {
        AiChatLog log = new AiChatLog();
        log.setCompanyId(companyId);
        log.setUserId(userId);
        log.setSessionId(sessionId);
        log.setRole(role);
        log.setMessageType(messageType);
        log.setContent(content);
        log.setMetadataJson(writeMetadata(metadata));
        aiChatLogMapper.insert(log);
        return log;
    }

    public void updateMessageMetadata(Long messageId, Map<String, Object> metadata) {
        AiChatLog update = new AiChatLog();
        update.setId(messageId);
        update.setMetadataJson(writeMetadata(metadata));
        aiChatLogMapper.updateById(update);
    }

    public List<ChatMessage> loadRecentConversation(Long companyId, Long userId, String sessionId, int rounds) {
        int limit = Math.max(1, rounds * 2);
        LambdaQueryWrapper<AiChatLog> wrapper = new LambdaQueryWrapper<AiChatLog>()
                .eq(AiChatLog::getCompanyId, companyId)
                .eq(AiChatLog::getUserId, userId)
                .eq(AiChatLog::getSessionId, sessionId)
                .orderByDesc(AiChatLog::getCreateTime)
                .orderByDesc(AiChatLog::getId)
                .last("LIMIT " + limit);

        List<AiChatLog> logs = aiChatLogMapper.selectList(wrapper);
        Collections.reverse(logs);

        List<ChatMessage> history = new ArrayList<>();
        for (AiChatLog log : logs) {
            if (AiConstants.MESSAGE_TYPE_ACTION_REQUIRED.equals(log.getMessageType())) {
                continue;
            }
            if ("user".equals(log.getRole())) {
                history.add(UserMessage.from(log.getContent()));
            } else if ("assistant".equals(log.getRole()) || "system".equals(log.getRole())) {
                history.add(AiMessage.from(log.getContent()));
            }
        }
        return history;
    }

    public List<AiSessionVO> listSessions(Long companyId, Long userId) {
        LambdaQueryWrapper<AiChatLog> wrapper = new LambdaQueryWrapper<AiChatLog>()
                .eq(AiChatLog::getCompanyId, companyId)
                .eq(AiChatLog::getUserId, userId)
                .orderByDesc(AiChatLog::getCreateTime)
                .orderByDesc(AiChatLog::getId);

        List<AiChatLog> logs = aiChatLogMapper.selectList(wrapper);
        Map<String, AiChatLog> latestBySession = new LinkedHashMap<>();
        for (AiChatLog log : logs) {
            latestBySession.putIfAbsent(log.getSessionId(), log);
        }

        List<AiSessionVO> sessions = new ArrayList<>();
        for (Map.Entry<String, AiChatLog> entry : latestBySession.entrySet()) {
            String sessionId = entry.getKey();
            AiChatLog latestMessage = entry.getValue();

            AiSessionVO session = new AiSessionVO();
            session.setSessionId(sessionId);
            session.setTitle(resolveSessionTitle(companyId, userId, sessionId));
            session.setLastMessagePreview(buildPreview(latestMessage.getContent()));
            session.setLastMessageTime(latestMessage.getCreateTime());
            sessions.add(session);
        }
        return sessions;
    }

    public List<AiChatMessageVO> listMessages(Long companyId, Long userId, String sessionId) {
        LambdaQueryWrapper<AiChatLog> wrapper = new LambdaQueryWrapper<AiChatLog>()
                .eq(AiChatLog::getCompanyId, companyId)
                .eq(AiChatLog::getUserId, userId)
                .eq(AiChatLog::getSessionId, sessionId)
                .orderByAsc(AiChatLog::getCreateTime)
                .orderByAsc(AiChatLog::getId);

        return aiChatLogMapper.selectList(wrapper).stream()
                .map(log -> {
                    AiChatMessageVO message = new AiChatMessageVO();
                    message.setId(log.getId());
                    message.setRole(log.getRole());
                    message.setMessageType(log.getMessageType());
                    message.setContent(log.getContent());
                    message.setMetadata(readMetadata(log.getMetadataJson()));
                    message.setCreateTime(log.getCreateTime());
                    return message;
                })
                .toList();
    }

    public Map<String, Object> readMetadata(String rawMetadata) {
        if (rawMetadata == null || rawMetadata.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(rawMetadata, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new BusinessException("AI 历史消息元数据解析失败");
        }
    }

    private String writeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new BusinessException("AI 历史消息元数据序列化失败");
        }
    }

    private String resolveSessionTitle(Long companyId, Long userId, String sessionId) {
        LambdaQueryWrapper<AiChatLog> wrapper = new LambdaQueryWrapper<AiChatLog>()
                .eq(AiChatLog::getCompanyId, companyId)
                .eq(AiChatLog::getUserId, userId)
                .eq(AiChatLog::getSessionId, sessionId)
                .eq(AiChatLog::getRole, "user")
                .orderByAsc(AiChatLog::getCreateTime)
                .orderByAsc(AiChatLog::getId)
                .last("LIMIT 1");

        AiChatLog firstUserMessage = aiChatLogMapper.selectOne(wrapper);
        if (firstUserMessage == null || firstUserMessage.getContent() == null || firstUserMessage.getContent().isBlank()) {
            return "新对话";
        }
        String title = firstUserMessage.getContent().trim();
        return title.length() <= AiConstants.CHAT_TITLE_MAX_LENGTH
                ? title
                : title.substring(0, AiConstants.CHAT_TITLE_MAX_LENGTH) + "...";
    }

    private String buildPreview(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 36 ? normalized : normalized.substring(0, 36) + "...";
    }
}
