package com.pjh.server.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class TaxDashboardVO {

    private BigDecimal taxBurdenRate;
    private BigDecimal positiveTaxAmount;
    private BigDecimal incomeBase;
    private BigDecimal unpaidTaxAmount;
    private List<Integer> availableYears;
    private Integer selectedYear;
    private List<TaxTypeStructureItem> taxTypeStructure;
    private List<StatusSummaryItem> statusSummary;
    private PeriodComparison periodComparison;
    private List<OutstandingItem> recentOutstanding;

    @Data
    public static class TaxTypeStructureItem {
        private String taxType;
        private BigDecimal amount;
        private BigDecimal ratio;
    }

    @Data
    public static class StatusSummaryItem {
        private Integer status;
        private Long count;
        private BigDecimal amount;
    }

    @Data
    public static class PeriodComparison {
        private String baselineLabel;
        private BigDecimal previousTaxBurdenRate;
        private BigDecimal burdenRateDelta;
        private BigDecimal previousUnpaidTaxAmount;
        private BigDecimal unpaidTaxAmountDelta;
        private BigDecimal previousPositiveTaxAmount;
        private BigDecimal positiveTaxAmountDelta;
    }

    @Data
    public static class OutstandingItem {
        private String taxPeriod;
        private String taxType;
        private BigDecimal amount;
    }
}
