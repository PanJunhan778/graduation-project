package com.pjh.server.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class HomeDashboardVO {

    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netProfit;
    private BigDecimal unpaidTax;
    private boolean hasUnpaidWarning;
    private List<MonthlyTrendPoint> monthlyTrend;
    private List<TaxCalendarItem> taxCalendar;
    private SetupStatus setupStatus;

    @Data
    public static class MonthlyTrendPoint {
        private String month;
        private BigDecimal income;
        private BigDecimal expense;
        private BigDecimal profit;
    }

    @Data
    public static class TaxCalendarItem {
        private String taxPeriod;
        private String taxType;
        private Integer status;
        private BigDecimal amount;
    }

    @Data
    public static class SetupStatus {
        private boolean hasStaffAccount;
        private boolean hasFinanceRecord;
    }
}
