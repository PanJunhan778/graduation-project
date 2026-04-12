package com.pjh.server.service;

import com.pjh.server.common.AiConstants;
import com.pjh.server.entity.AiChatLog;
import com.pjh.server.entity.AiPendingAction;
import com.pjh.server.entity.Company;
import com.pjh.server.mapper.AiPendingActionMapper;
import com.pjh.server.mapper.CompanyMapper;
import com.pjh.server.vo.AiConfirmActionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiHitlServiceTest {

    @Mock
    private AiPendingActionMapper aiPendingActionMapper;

    @Mock
    private AiHistoryService aiHistoryService;

    @Mock
    private CompanyMapper companyMapper;

    private AiHitlService aiHitlService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-12T03:00:00Z"), ZoneId.of("Asia/Shanghai"));
        aiHitlService = new AiHitlService(aiPendingActionMapper, aiHistoryService, companyMapper, fixedClock);
    }

    @Test
    void createCompanyDescriptionPendingActionShouldPersistActionAndCardMessage() {
        doAnswer(invocation -> {
            AiPendingAction action = invocation.getArgument(0);
            action.setId(101L);
            return 1;
        }).when(aiPendingActionMapper).insert(any(AiPendingAction.class));

        AiChatLog log = new AiChatLog();
        log.setId(501L);
        when(aiHistoryService.createMessage(any(), any(), any(), any(), any(), any(), any())).thenReturn(log);

        var payload = aiHitlService.createCompanyDescriptionPendingAction(
                1L,
                2L,
                "session-1",
                "旧描述",
                "新描述"
        );

        assertThat(payload.getActionId()).isEqualTo(101L);
        assertThat(payload.getToolName()).isEqualTo(AiConstants.ACTION_TYPE_UPDATE_COMPANY_DESCRIPTION);
        assertThat(payload.getOldValue()).isEqualTo("旧描述");
        assertThat(payload.getProposedValue()).isEqualTo("新描述");
        assertThat(payload.getConfirmToken()).isNotBlank();

        ArgumentCaptor<AiPendingAction> updateCaptor = ArgumentCaptor.forClass(AiPendingAction.class);
        verify(aiPendingActionMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getChatMessageId()).isEqualTo(501L);
    }

    @Test
    void confirmActionShouldUpdateCompanyDescriptionWhenApproved() {
        AiPendingAction action = new AiPendingAction();
        action.setId(88L);
        action.setCompanyId(9L);
        action.setUserId(2L);
        action.setSessionId("session-9");
        action.setChatMessageId(700L);
        action.setActionType(AiConstants.ACTION_TYPE_UPDATE_COMPANY_DESCRIPTION);
        action.setConfirmToken("token-1");
        action.setOldValue("旧业务");
        action.setProposedValue("新业务");
        action.setStatus(AiConstants.ACTION_STATUS_PENDING);
        action.setExpiresAt(LocalDateTime.of(2026, 4, 12, 12, 0));

        Company company = new Company();
        company.setId(9L);
        company.setDescription("旧业务");

        when(aiPendingActionMapper.selectOne(any())).thenReturn(action);
        when(companyMapper.selectById(9L)).thenReturn(company);

        AiConfirmActionVO result = aiHitlService.confirmAction(9L, 2L, "token-1", true);

        assertThat(result.getActionId()).isEqualTo(88L);
        assertThat(result.getStatus()).isEqualTo(AiConstants.ACTION_STATUS_APPROVED);
        assertThat(result.getResultMessage()).contains("已同意");
        assertThat(company.getDescription()).isEqualTo("新业务");

        verify(companyMapper).updateById(company);
        verify(aiHistoryService).updateMessageMetadata(any(), any());
        verify(aiHistoryService).createMessage(any(), any(), any(), any(), any(), any(), any());
    }
}
