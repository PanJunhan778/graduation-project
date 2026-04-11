package com.pjh.server.audit;

import com.pjh.server.entity.Employee;
import com.pjh.server.entity.FinanceRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditCreateDeleteDiffBuilderTest {

    private final AuditDiffBuilder auditDiffBuilder = new AuditDiffBuilder();

    @Test
    void buildCreateChangesShouldGenerateNewValueOnlyEntries() {
        FinanceRecord record = new FinanceRecord();
        record.setType("income");
        record.setAmount(new BigDecimal("5200.00"));
        record.setCategory("销售收入");
        record.setProject("新客户签约");
        record.setDate(LocalDate.of(2026, 4, 11));
        record.setRemark("first record");

        List<AuditFieldChange> changes = auditDiffBuilder.buildCreateChanges(
                record,
                new String[]{"type", "amount", "category", "project", "date", "remark"}
        );

        assertEquals(6, changes.size());
        assertEquals("type", changes.get(0).fieldName());
        assertEquals("income", changes.get(0).newValue());
        assertEquals("remark", changes.get(5).fieldName());
        assertEquals("first record", changes.get(5).newValue());
    }

    @Test
    void buildDeleteChangesShouldGenerateOldValueOnlyEntries() {
        Employee employee = new Employee();
        employee.setName("张三");
        employee.setDepartment("市场部");
        employee.setPosition("招商主管");
        employee.setSalary(new BigDecimal("9300.00"));
        employee.setHireDate(LocalDate.of(2026, 4, 1));
        employee.setStatus(1);
        employee.setRemark("试用期");

        List<AuditFieldChange> changes = auditDiffBuilder.buildDeleteChanges(
                employee,
                new String[]{"name", "department", "position", "salary", "hireDate", "status", "remark"}
        );

        assertEquals(7, changes.size());
        assertEquals("name", changes.get(0).fieldName());
        assertEquals("张三", changes.get(0).oldValue());
        assertEquals("remark", changes.get(6).fieldName());
        assertEquals("试用期", changes.get(6).oldValue());
    }
}
