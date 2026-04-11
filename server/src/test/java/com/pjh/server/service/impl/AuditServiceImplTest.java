package com.pjh.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pjh.server.entity.AuditLog;
import com.pjh.server.entity.User;
import com.pjh.server.exception.BusinessException;
import com.pjh.server.mapper.AuditLogMapper;
import com.pjh.server.mapper.UserMapper;
import com.pjh.server.util.CurrentSessionService;
import com.pjh.server.vo.AuditLogVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock
    private AuditLogMapper auditLogMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CurrentSessionService currentSessionService;

    @InjectMocks
    private AuditServiceImpl auditService;

    @Test
    void listAuditLogsShouldMapOperatorNameAndApplyCurrentCompanyFilter() {
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(88L);
        when(auditLogMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<AuditLog> page = invocation.getArgument(0);
            page.setRecords(List.of(
                    auditLog(1L, 11L, "remark", "old remark", "new remark"),
                    auditLog(2L, 12L, "salary", "8000", "9500"),
                    auditLog(3L, 13L, "department", "市场部", "销售部")
            ));
            page.setTotal(3);
            return page;
        });
        when(userMapper.selectBatchIds(List.of(11L, 12L, 13L))).thenReturn(List.of(
                user(11L, "Owner User", "owner01"),
                user(12L, null, "staff02")
        ));

        IPage<AuditLogVO> result = auditService.listAuditLogs(
                1,
                20,
                "finance",
                "UPDATE",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 11)
        );

        assertEquals(3, result.getRecords().size());
        assertEquals("Owner User", result.getRecords().get(0).getOperatorName());
        assertEquals("staff02", result.getRecords().get(1).getOperatorName());
        assertTrue(result.getRecords().get(2).getOperatorName().contains("13"));
        assertEquals("UPDATE", result.getRecords().get(0).getOperationType());

        ArgumentCaptor<Wrapper<AuditLog>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(auditLogMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("company_id"));
        assertTrue(sqlSegment.contains("module"));
        assertTrue(sqlSegment.contains("operation_type"));
        assertTrue(sqlSegment.contains("operation_time"));
    }

    @Test
    void listAuditLogsShouldAllowQueryWithoutModule() {
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(88L);
        when(auditLogMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> invocation.getArgument(0));

        auditService.listAuditLogs(1, 20, null, null, null, null);

        ArgumentCaptor<Wrapper<AuditLog>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(auditLogMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        assertTrue(wrapperCaptor.getValue().getSqlSegment().contains("company_id"));
    }

    @Test
    void listAuditLogsShouldRejectUnsupportedModule() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> auditService.listAuditLogs(1, 20, "invalid", null, null, null)
        );

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("finance"));
    }

    @Test
    void listAuditLogsShouldRejectInvalidDateRange() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> auditService.listAuditLogs(
                        1,
                        20,
                        "finance",
                        null,
                        LocalDate.of(2026, 4, 12),
                        LocalDate.of(2026, 4, 11)
                )
        );

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("日期"));
    }

    @Test
    void listAuditLogsShouldRejectUnsupportedOperationType() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> auditService.listAuditLogs(1, 20, "finance", "archive", null, null)
        );

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("operationType"));
    }

    private AuditLog auditLog(Long id, Long userId, String fieldName, String oldValue, String newValue) {
        AuditLog auditLog = new AuditLog();
        auditLog.setId(id);
        auditLog.setCompanyId(88L);
        auditLog.setUserId(userId);
        auditLog.setModule("finance");
        auditLog.setOperationType("UPDATE");
        auditLog.setTargetId(99L);
        auditLog.setFieldName(fieldName);
        auditLog.setOldValue(oldValue);
        auditLog.setNewValue(newValue);
        auditLog.setOperationTime(LocalDateTime.of(2026, 4, 11, 10, 30, 0));
        return auditLog;
    }

    private User user(Long id, String realName, String username) {
        User user = new User();
        user.setId(id);
        user.setRealName(realName);
        user.setUsername(username);
        return user;
    }
}
