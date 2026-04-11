package com.pjh.server.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class FinanceDashboardVO {

    private BigDecimal totalExpense;
    private BigDecimal totalIncome;
    private List<ExpenseBreakdownItem> expenseBreakdown;
    private List<TopIncomeSourceItem> topIncomeSources;

    @Data
    public static class ExpenseBreakdownItem {
        private String name;
        private BigDecimal amount;
        private BigDecimal ratio;
    }

    @Data
    public static class TopIncomeSourceItem {
        private String name;
        private BigDecimal amount;
    }
}
