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
    private List<MonthlyTrendItem> monthlyTrend;
    private IncomeConcentration incomeConcentration;
    private PeriodComparison periodComparison;

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

    @Data
    public static class MonthlyTrendItem {
        private String month;
        private BigDecimal income;
        private BigDecimal expense;
        private BigDecimal profit;
    }

    @Data
    public static class IncomeConcentration {
        private BigDecimal top1Share;
        private BigDecimal top3Share;
        private BigDecimal top5Share;
        private BigDecimal otherShare;
        private Integer sourceCount;
    }

    @Data
    public static class PeriodComparison {
        private BigDecimal incomeChange;
        private BigDecimal expenseChange;
        private BigDecimal profitChange;
        private String baselineLabel;
    }
}
