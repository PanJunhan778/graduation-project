package com.pjh.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pjh.server.audit.AuditOperationService;
import com.pjh.server.audit.DeleteAuditMetadata;
import com.pjh.server.audit.DeleteAuditMetadataResolver;
import com.pjh.server.dto.EmployeeUpsertDTO;
import com.pjh.server.entity.Employee;
import com.pjh.server.entity.User;
import com.pjh.server.mapper.EmployeeMapper;
import com.pjh.server.util.CurrentSessionService;
import com.pjh.server.vo.EmployeeRecycleBinVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock
    private EmployeeMapper employeeMapper;

    @Mock
    private AuditOperationService auditOperationService;

    @Mock
    private DeleteAuditMetadataResolver deleteAuditMetadataResolver;

    @Mock
    private CurrentSessionService currentSessionService;

    @InjectMocks
    private EmployeeServiceImpl employeeService;

    @Test
    void updateEmployeeShouldNormalizeOptionalFields() {
        Employee existing = new Employee();
        existing.setId(1L);
        when(employeeMapper.selectById(1L)).thenReturn(existing);

        EmployeeUpsertDTO dto = new EmployeeUpsertDTO();
        dto.setName(" 张三 ");
        dto.setDepartment(" 销售部 ");
        dto.setPosition("   ");
        dto.setSalary(new BigDecimal("8000.00"));
        dto.setHireDate(LocalDate.of(2026, 4, 9));
        dto.setStatus(1);
        dto.setRemark("  ");

        employeeService.updateEmployee(1L, dto);

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeMapper).updateById(captor.capture());
        assertEquals("张三", captor.getValue().getName());
        assertEquals("销售部", captor.getValue().getDepartment());
        assertNull(captor.getValue().getPosition());
        assertNull(captor.getValue().getRemark());
    }

    @Test
    void listRecycleBinEmployeesShouldFallbackToUpdatedByWhenDeleteAuditMissing() {
        Employee employee = employee(5L, 22L, LocalDateTime.of(2026, 4, 10, 9, 0));
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(88L);
        when(employeeMapper.selectDeletedByCompanyId(88L)).thenReturn(List.of(employee));
        when(deleteAuditMetadataResolver.resolve("employee", 88L, List.of(5L))).thenReturn(Map.of());
        when(deleteAuditMetadataResolver.loadUsers(Set.of(22L))).thenReturn(Map.of(
                22L, user(22L, "回退操作人", "staff22")
        ));
        when(deleteAuditMetadataResolver.resolveOperatorName(eq(22L), any(User.class))).thenReturn("回退操作人");

        IPage<EmployeeRecycleBinVO> result = employeeService.listRecycleBinEmployees(1, 20);

        assertEquals(1L, result.getTotal());
        assertEquals("回退操作人", result.getRecords().get(0).getDeletedByName());
        assertEquals(employee.getUpdatedTime(), result.getRecords().get(0).getDeletedTime());
    }

    @Test
    void restoreEmployeeShouldPublishRestoreAudit() {
        Employee deletedEmployee = employee(7L, 12L, LocalDateTime.of(2026, 4, 10, 9, 0));
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(88L);
        when(currentSessionService.requireCurrentUserId()).thenReturn(66L);
        when(employeeMapper.selectDeletedById(88L, 7L)).thenReturn(deletedEmployee);
        when(employeeMapper.restoreDeletedById(88L, 7L, 66L)).thenReturn(1);

        employeeService.restoreEmployee(7L);

        verify(auditOperationService).publishRestore(eq("employee"), eq(7L), same(deletedEmployee), any());
    }

    @Test
    void batchRestoreShouldRestoreOnlyMatchedDeletedEmployees() {
        Employee first = employee(8L, 12L, LocalDateTime.of(2026, 4, 10, 9, 0));
        Employee second = employee(9L, 13L, LocalDateTime.of(2026, 4, 11, 9, 0));
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(88L);
        when(currentSessionService.requireCurrentUserId()).thenReturn(66L);
        when(employeeMapper.selectDeletedByIds(88L, List.of(8L, 9L, 999L))).thenReturn(List.of(first, second));
        when(employeeMapper.restoreDeletedBatch(88L, List.of(8L, 9L), 66L)).thenReturn(2);

        int restoredCount = employeeService.batchRestore(List.of(8L, 9L, 999L));

        assertEquals(2, restoredCount);
        verify(auditOperationService).publishRestore(eq("employee"), eq(8L), same(first), any());
        verify(auditOperationService).publishRestore(eq("employee"), eq(9L), same(second), any());
    }

    private Employee employee(Long id, Long updatedBy, LocalDateTime updatedTime) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setName("员工" + id);
        employee.setDepartment("销售部");
        employee.setPosition("顾问");
        employee.setSalary(new BigDecimal("9000.00"));
        employee.setHireDate(LocalDate.of(2025, 1, 1));
        employee.setStatus(1);
        employee.setUpdatedBy(updatedBy);
        employee.setUpdatedTime(updatedTime);
        employee.setCreatedTime(updatedTime.minusDays(10));
        return employee;
    }

    private User user(Long id, String realName, String username) {
        User user = new User();
        user.setId(id);
        user.setRealName(realName);
        user.setUsername(username);
        return user;
    }
}
