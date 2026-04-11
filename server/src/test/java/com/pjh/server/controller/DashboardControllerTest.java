package com.pjh.server.controller;

import com.pjh.server.service.DashboardService;
import com.pjh.server.vo.FinanceDashboardVO;
import com.pjh.server.vo.HomeDashboardVO;
import com.pjh.server.vo.HrDashboardVO;
import com.pjh.server.vo.TaxDashboardVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DashboardController(dashboardService))
                .build();
    }

    @Test
    void getHomeDashboardShouldReturnWrappedDashboardData() throws Exception {
        HomeDashboardVO dashboard = new HomeDashboardVO();
        dashboard.setTotalIncome(new BigDecimal("12000.00"));
        dashboard.setTotalExpense(new BigDecimal("7000.00"));
        dashboard.setNetProfit(new BigDecimal("5000.00"));
        dashboard.setUnpaidTax(new BigDecimal("3200.00"));
        dashboard.setHasUnpaidWarning(true);

        HomeDashboardVO.MonthlyTrendPoint point = new HomeDashboardVO.MonthlyTrendPoint();
        point.setMonth("2026-04");
        point.setIncome(new BigDecimal("12000.00"));
        point.setExpense(new BigDecimal("7000.00"));
        point.setProfit(new BigDecimal("5000.00"));
        dashboard.setMonthlyTrend(List.of(point));

        HomeDashboardVO.TaxCalendarItem item = new HomeDashboardVO.TaxCalendarItem();
        item.setTaxPeriod("2026-Q1");
        item.setTaxType("增值税");
        item.setStatus(0);
        item.setAmount(new BigDecimal("3200.00"));
        dashboard.setTaxCalendar(List.of(item));

        when(dashboardService.getHomeDashboard()).thenReturn(dashboard);

        mockMvc.perform(get("/api/dashboard/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalIncome").value(12000.00))
                .andExpect(jsonPath("$.data.totalExpense").value(7000.00))
                .andExpect(jsonPath("$.data.netProfit").value(5000.00))
                .andExpect(jsonPath("$.data.unpaidTax").value(3200.00))
                .andExpect(jsonPath("$.data.hasUnpaidWarning").value(true))
                .andExpect(jsonPath("$.data.monthlyTrend[0].month").value("2026-04"))
                .andExpect(jsonPath("$.data.taxCalendar[0].taxType").value("增值税"));

        verify(dashboardService).getHomeDashboard();
    }

    @Test
    void getFinanceDashboardShouldPassRangeAndReturnWrappedData() throws Exception {
        FinanceDashboardVO dashboard = new FinanceDashboardVO();
        dashboard.setTotalIncome(new BigDecimal("68000.00"));
        dashboard.setTotalExpense(new BigDecimal("31000.00"));

        FinanceDashboardVO.ExpenseBreakdownItem expenseItem = new FinanceDashboardVO.ExpenseBreakdownItem();
        expenseItem.setName("营销");
        expenseItem.setAmount(new BigDecimal("12000.00"));
        expenseItem.setRatio(new BigDecimal("0.3871"));
        dashboard.setExpenseBreakdown(List.of(expenseItem));

        FinanceDashboardVO.TopIncomeSourceItem incomeItem = new FinanceDashboardVO.TopIncomeSourceItem();
        incomeItem.setName("企业年框项目");
        incomeItem.setAmount(new BigDecimal("28000.00"));
        dashboard.setTopIncomeSources(List.of(incomeItem));

        when(dashboardService.getFinanceDashboard("last12months")).thenReturn(dashboard);

        mockMvc.perform(get("/api/dashboard/finance").param("range", "last12months"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalIncome").value(68000.00))
                .andExpect(jsonPath("$.data.totalExpense").value(31000.00))
                .andExpect(jsonPath("$.data.expenseBreakdown[0].name").value("营销"))
                .andExpect(jsonPath("$.data.topIncomeSources[0].name").value("企业年框项目"));

        verify(dashboardService).getFinanceDashboard("last12months");
    }

    @Test
    void getHrDashboardShouldUseDefaultRangeAndReturnWrappedData() throws Exception {
        HrDashboardVO dashboard = new HrDashboardVO();
        dashboard.setActiveEmployeeCount(18L);
        dashboard.setActiveSalaryTotal(new BigDecimal("196000.00"));

        HrDashboardVO.DepartmentSalaryShareItem shareItem = new HrDashboardVO.DepartmentSalaryShareItem();
        shareItem.setDepartment("产品部");
        shareItem.setEmployeeCount(6L);
        shareItem.setSalaryAmount(new BigDecimal("76000.00"));
        shareItem.setRatio(new BigDecimal("0.3878"));
        dashboard.setDepartmentSalaryShare(List.of(shareItem));

        HrDashboardVO.MonthlyTrendItem trendItem = new HrDashboardVO.MonthlyTrendItem();
        trendItem.setMonth("2026-04");
        trendItem.setEmployeeCount(18L);
        trendItem.setSalaryAmount(new BigDecimal("196000.00"));
        dashboard.setMonthlyTrend(List.of(trendItem));

        when(dashboardService.getHrDashboard("last6months")).thenReturn(dashboard);

        mockMvc.perform(get("/api/dashboard/hr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.activeEmployeeCount").value(18))
                .andExpect(jsonPath("$.data.departmentSalaryShare[0].department").value("产品部"))
                .andExpect(jsonPath("$.data.monthlyTrend[0].month").value("2026-04"));

        verify(dashboardService).getHrDashboard("last6months");
    }

    @Test
    void getTaxDashboardShouldUseDefaultRangeAndReturnWrappedData() throws Exception {
        TaxDashboardVO dashboard = new TaxDashboardVO();
        dashboard.setTaxBurdenRate(new BigDecimal("0.0835"));
        dashboard.setPositiveTaxAmount(new BigDecimal("42000.00"));
        dashboard.setIncomeBase(new BigDecimal("503000.00"));
        dashboard.setUnpaidTaxAmount(new BigDecimal("9600.00"));

        TaxDashboardVO.TaxTypeStructureItem structureItem = new TaxDashboardVO.TaxTypeStructureItem();
        structureItem.setTaxType("增值税");
        structureItem.setAmount(new BigDecimal("26000.00"));
        structureItem.setRatio(new BigDecimal("0.6190"));
        dashboard.setTaxTypeStructure(List.of(structureItem));

        TaxDashboardVO.StatusSummaryItem summaryItem = new TaxDashboardVO.StatusSummaryItem();
        summaryItem.setStatus(0);
        summaryItem.setCount(2L);
        summaryItem.setAmount(new BigDecimal("9600.00"));
        dashboard.setStatusSummary(List.of(summaryItem));

        when(dashboardService.getTaxDashboard("thisYear")).thenReturn(dashboard);

        mockMvc.perform(get("/api/dashboard/tax"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taxBurdenRate").value(0.0835))
                .andExpect(jsonPath("$.data.positiveTaxAmount").value(42000.00))
                .andExpect(jsonPath("$.data.taxTypeStructure[0].taxType").value("增值税"))
                .andExpect(jsonPath("$.data.statusSummary[0].status").value(0));

        verify(dashboardService).getTaxDashboard("thisYear");
    }
}
