package com.pjh.server.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class HrDashboardVO {

    private Long activeEmployeeCount;
    private BigDecimal activeSalaryTotal;
    private List<DepartmentSalaryShareItem> departmentSalaryShare;
    private List<MonthlyTrendItem> monthlyTrend;

    @Data
    public static class DepartmentSalaryShareItem {
        private String department;
        private Long employeeCount;
        private BigDecimal salaryAmount;
        private BigDecimal ratio;
    }

    @Data
    public static class MonthlyTrendItem {
        private String month;
        private Long employeeCount;
        private BigDecimal salaryAmount;
    }
}
