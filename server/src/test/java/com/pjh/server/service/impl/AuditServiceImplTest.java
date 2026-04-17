package com.pjh.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pjh.server.audit.AuditOperationGroupKey;
import com.pjh.server.audit.AuditOperationGroupQuery;
import com.pjh.server.audit.AuditOperationGroupRow;
import com.pjh.server.entity.AuditLog;
import com.pjh.server.entity.User;
import com.pjh.server.exception.BusinessException;
import com.pjh.server.mapper.AuditLogMapper;
import com.pjh.server.mapper.UserMapper;
import com.pjh.server.util.CurrentSessionService;
import com.pjh.server.vo.AuditOperationVO;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    void listAuditLogsShouldGroupFieldChangesIntoOperationRecords() {
        LocalDateTime financeTime = LocalDateTime.of(2026, 4, 11, 10, 30, 0);
        LocalDateTime employeeTime = LocalDateTime.of(2026, 4, 10, 9, 15, 0);

        when(currentSessionService.requireCurrentCompanyId()).thenReturn(88L);
        when(auditLogMapper.countOperationGroups(any(AuditOperationGroupQuery.class))).thenReturn(2L);
        when(auditLogMapper.selectOperationGroups(any(AuditOperationGroupQuery.class))).thenReturn(List.of(
                groupRow("finance", "UPDATE", 99L, 11L, financeTime, 102L, 2L),
                groupRow("employee", "UPDATE", 77L, 12L, employeeTime, 201L, 1L)
        ));
        when(auditLogMapper.selectByOperationGroups(eq(88L), anyList())).thenReturn(List.of(
                auditLog(101L, 11L, "finance", "UPDATE", 99L, "category", "采购支出", "销售收入", financeTime),
                auditLog(102L, 11L, "finance", "UPDATE", 99L, "remark", "旧备注", "新备注", financeTime),
                auditLog(201L, 12L, "employee", "UPDATE", 77L, "salary", "8000", "9500", employeeTime)
        ));
        when(userMapper.selectBatchIds(List.of(11L, 12L))).thenReturn(List.of(
                user(11L, "Owner User", "owner01"),
                user(12L, null, "staff02")
        ));

        IPage<AuditOperationVO> result = auditService.listAuditLogs(
                1,
                20,
                "finance",
                "UPDATE",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 11)
        );

        assertEquals(2L, result.getTotal());
        assertEquals(2, result.getRecords().size());
        assertEquals("Owner User", result.getRecords().get(0).getOperatorName());
        assertEquals(2, result.getRecords().get(0).getChangeCount());
        assertEquals(2, result.getRecords().get(0).getChanges().size());
        assertEquals("remark", result.getRecords().get(0).getChanges().get(1).getFieldName());
        assertEquals("staff02", result.getRecords().get(1).getOperatorName());
        assertEquals("salary", result.getRecords().get(1).getChanges().get(0).getFieldName());

        ArgumentCaptor<AuditOperationGroupQuery> queryCaptor = ArgumentCaptor.forClass(AuditOperationGroupQuery.class);
        verify(auditLogMapper).countOperationGroups(queryCaptor.capture());
        AuditOperationGroupQuery query = queryCaptor.getValue();
        assertEquals(88L, query.getCompanyId());
        assertEquals("finance", query.getModule());
        assertEquals("UPDATE", query.getOperationType());
        assertEquals(LocalDateTime.of(2026, 4, 1, 0, 0), query.getStartDateTime());
        assertEquals(LocalDateTime.of(2026, 4, 12, 0, 0), query.getEndExclusive());

        ArgumentCaptor<List<AuditOperationGroupKey>> keyCaptor = ArgumentCaptor.forClass(List.class);
        verify(auditLogMapper).selectByOperationGroups(eq(88L), keyCaptor.capture());
        assertEquals(2, keyCaptor.getValue().size());
        assertEquals(financeTime, keyCaptor.getValue().get(0).getOperationTime());
    }

    @Test
    void listAuditLogsShouldReturnEmptyPageWhenNoOperationGroupsExist() {
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(88L);
        when(auditLogMapper.countOperationGroups(any(AuditOperationGroupQuery.class))).thenReturn(0L);

        IPage<AuditOperationVO> result = auditService.listAuditLogs(1, 20, null, null, null, null);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
        verify(auditLogMapper, never()).selectOperationGroups(any(AuditOperationGroupQuery.class));
        verify(auditLogMapper, never()).selectByOperationGroups(eq(88L), anyList());
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

    private AuditOperationGroupRow groupRow(
            String module,
            String operationType,
            Long targetId,
            Long userId,
            LocalDateTime operationTime,
            Long maxId,
            Long changeCount
    ) {
        AuditOperationGroupRow row = new AuditOperationGroupRow();
        row.setModule(module);
        row.setOperationType(operationType);
        row.setTargetId(targetId);
        row.setUserId(userId);
        row.setOperationTime(operationTime);
        row.setMaxId(maxId);
        row.setChangeCount(changeCount);
        return row;
    }

    private AuditLog auditLog(
            Long id,
            Long userId,
            String module,
            String operationType,
            Long targetId,
            String fieldName,
            String oldValue,
            String newValue,
            LocalDateTime operationTime
    ) {
        AuditLog auditLog = new AuditLog();
        auditLog.setId(id);
        auditLog.setCompanyId(88L);
        auditLog.setUserId(userId);
        auditLog.setModule(module);
        auditLog.setOperationType(operationType);
        auditLog.setTargetId(targetId);
        auditLog.setFieldName(fieldName);
        auditLog.setOldValue(oldValue);
        auditLog.setNewValue(newValue);
        auditLog.setOperationTime(operationTime);
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
