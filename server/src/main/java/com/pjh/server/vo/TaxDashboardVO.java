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
    private List<TaxTypeStructureItem> taxTypeStructure;
    private List<StatusSummaryItem> statusSummary;

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
}
