package com.pjh.server.audit;

import com.pjh.server.entity.Employee;
import com.pjh.server.entity.FinanceRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditDiffBuilderTest {

    private final AuditDiffBuilder auditDiffBuilder = new AuditDiffBuilder();

    @Test
    void buildChangesShouldReturnOnlyRemarkWhenOnlyRemarkChanges() {
        FinanceRecord before = financeRecord("旧备注", new BigDecimal("1000.00"));
        FinanceRecord after = financeRecord("新备注", new BigDecimal("1000.00"));

        List<AuditFieldChange> changes = auditDiffBuilder.buildChanges(
                before,
                after,
                new String[]{"type", "amount", "category", "project", "date", "remark"}
        );

        assertEquals(1, changes.size());
        assertEquals("remark", changes.get(0).fieldName());
        assertEquals("旧备注", changes.get(0).oldValue());
        assertEquals("新备注", changes.get(0).newValue());
    }

    @Test
    void buildChangesShouldCaptureBigDecimalChangeAccurately() {
        Employee before = employee(new BigDecimal("8000.00"));
        Employee after = employee(new BigDecimal("9600.50"));

        List<AuditFieldChange> changes = auditDiffBuilder.buildChanges(
                before,
                after,
                new String[]{"name", "department", "position", "salary", "hireDate", "status", "remark"}
        );

        assertEquals(1, changes.size());
        assertEquals("salary", changes.get(0).fieldName());
        assertEquals("8000", changes.get(0).oldValue());
        assertEquals("9600.5", changes.get(0).newValue());
    }

    @Test
    void buildChangesShouldReturnEmptyWhenValuesStayTheSame() {
        FinanceRecord before = financeRecord("备注", new BigDecimal("1000.00"));
        FinanceRecord after = financeRecord("备注", new BigDecimal("1000.0"));

        List<AuditFieldChange> changes = auditDiffBuilder.buildChanges(
                before,
                after,
                new String[]{"type", "amount", "category", "project", "date", "remark"}
        );

        assertTrue(changes.isEmpty());
    }

    @Test
    void buildChangesShouldTreatNullAndBlankStringAsTheSameOptionalValue() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("project", null);
        before.put("remark", null);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("project", "");
        after.put("remark", "   ");

        List<AuditFieldChange> changes = auditDiffBuilder.buildChanges(
                before,
                after,
                new String[]{"project", "remark"}
        );

        assertTrue(changes.isEmpty());
    }

    private FinanceRecord financeRecord(String remark, BigDecimal amount) {
        FinanceRecord record = new FinanceRecord();
        record.setType("expense");
        record.setAmount(amount);
        record.setCategory("采购支出");
        record.setProject("供应链项目");
        record.setDate(LocalDate.of(2026, 4, 11));
        record.setRemark(remark);
        return record;
    }

    private Employee employee(BigDecimal salary) {
        Employee employee = new Employee();
        employee.setName("张三");
        employee.setDepartment("市场部");
        employee.setPosition("招商主管");
        employee.setSalary(salary);
        employee.setHireDate(LocalDate.of(2026, 4, 1));
        employee.setStatus(1);
        employee.setRemark("试用期");
        return employee;
    }
}
