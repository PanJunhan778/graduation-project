package com.pjh.server.controller;

import com.pjh.server.service.DashboardService;
import com.pjh.server.vo.FinanceDashboardVO;
import com.pjh.server.vo.HomeAiSummaryVO;
import com.pjh.server.vo.HomeDashboardVO;
import com.pjh.server.vo.HrDashboardVO;
import com.pjh.server.vo.TaxDashboardVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.hamcrest.Matchers;
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
        point.setMonth("2026-03");
        point.setIncome(new BigDecimal("12000.00"));
        point.setExpense(new BigDecimal("7000.00"));
        point.setProfit(new BigDecimal("5000.00"));
        dashboard.setMonthlyTrend(List.of(point));

        HomeDashboardVO.DepartmentHeadcountItem department = new HomeDashboardVO.DepartmentHeadcountItem();
        department.setDepartment("\u7814\u53d1\u90e8");
        department.setEmployeeCount(6L);
        dashboard.setDepartmentHeadcount(List.of(department));

        HomeDashboardVO.TaxCalendarItem item = new HomeDashboardVO.TaxCalendarItem();
        item.setTaxPeriod("2026-Q1");
        item.setTaxType("\u589e\u503c\u7a0e");
        item.setStatus(0);
        item.setAmount(new BigDecimal("3200.00"));
        dashboard.setTaxCalendar(List.of(item));

        HomeDashboardVO.SetupStatus setupStatus = new HomeDashboardVO.SetupStatus();
        setupStatus.setHasStaffAccount(true);
        setupStatus.setHasFinanceRecord(false);
        dashboard.setSetupStatus(setupStatus);

        when(dashboardService.getHomeDashboard()).thenReturn(dashboard);

        mockMvc.perform(get("/api/dashboard/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalIncome").value(12000.00))
                .andExpect(jsonPath("$.data.totalExpense").value(7000.00))
                .andExpect(jsonPath("$.data.netProfit").value(5000.00))
                .andExpect(jsonPath("$.data.unpaidTax").value(3200.00))
                .andExpect(jsonPath("$.data.hasUnpaidWarning").value(true))
                .andExpect(jsonPath("$.data.monthlyTrend[0].month").value("2026-03"))
                .andExpect(jsonPath("$.data.departmentHeadcount[0].department").value("\u7814\u53d1\u90e8"))
                .andExpect(jsonPath("$.data.departmentHeadcount[0].employeeCount").value(6))
                .andExpect(jsonPath("$.data.taxCalendar[0].taxType").value("\u589e\u503c\u7a0e"))
                .andExpect(jsonPath("$.data.setupStatus.hasStaffAccount").value(true))
                .andExpect(jsonPath("$.data.setupStatus.hasFinanceRecord").value(false));

        verify(dashboardService).getHomeDashboard();
    }

    @Test
    void getHomeAiSummaryShouldReturnWrappedSummaryData() throws Exception {
        HomeAiSummaryVO summary = new HomeAiSummaryVO();
        summary.setSummaryLines(List.of(
                "\u6700\u8fd1\u5b8c\u6574\u6708\u51c0\u5229\u6da6\u56de\u5347\u3002",
                "\u5f53\u524d\u4ecd\u6709\u5f85\u7f34\u7a0e\u989d\u9700\u8981\u5173\u6ce8\u3002"
        ));
        summary.setGeneratedAt("2026-04-13T09:30:00");
        summary.setStatus("ready");

        when(dashboardService.getHomeAiSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/dashboard/home-ai-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.summaryLines[0]").value("\u6700\u8fd1\u5b8c\u6574\u6708\u51c0\u5229\u6da6\u56de\u5347\u3002"))
                .andExpect(jsonPath("$.data.summaryLines[1]").value("\u5f53\u524d\u4ecd\u6709\u5f85\u7f34\u7a0e\u989d\u9700\u8981\u5173\u6ce8\u3002"))
                .andExpect(jsonPath("$.data.generatedAt").value("2026-04-13T09:30:00"))
                .andExpect(jsonPath("$.data.status").value("ready"));

        verify(dashboardService).getHomeAiSummary();
    }

    @Test
    void getHomeAiSummaryShouldAllowNullGeneratedAtForRefreshingState() throws Exception {
        HomeAiSummaryVO summary = new HomeAiSummaryVO();
        summary.setSummaryLines(List.of());
        summary.setGeneratedAt(null);
        summary.setStatus("refreshing");

        when(dashboardService.getHomeAiSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/dashboard/home-ai-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.generatedAt").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.status").value("refreshing"));

        verify(dashboardService).getHomeAiSummary();
    }

    @Test
    void getFinanceDashboardShouldPassRangeAndReturnWrappedData() throws Exception {
        FinanceDashboardVO dashboard = new FinanceDashboardVO();
        dashboard.setTotalIncome(new BigDecimal("68000.00"));
        dashboard.setTotalExpense(new BigDecimal("31000.00"));

        FinanceDashboardVO.ExpenseBreakdownItem expenseItem = new FinanceDashboardVO.ExpenseBreakdownItem();
        expenseItem.setName("\u8425\u9500");
        expenseItem.setAmount(new BigDecimal("12000.00"));
        expenseItem.setRatio(new BigDecimal("0.3871"));
        dashboard.setExpenseBreakdown(List.of(expenseItem));

        FinanceDashboardVO.TopIncomeSourceItem incomeItem = new FinanceDashboardVO.TopIncomeSourceItem();
        incomeItem.setName("\u4f01\u4e1a\u5e74\u6846\u9879\u76ee");
        incomeItem.setAmount(new BigDecimal("28000.00"));
        dashboard.setTopIncomeSources(List.of(incomeItem));

        when(dashboardService.getFinanceDashboard("last12months")).thenReturn(dashboard);

        mockMvc.perform(get("/api/dashboard/finance").param("range", "last12months"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalIncome").value(68000.00))
                .andExpect(jsonPath("$.data.totalExpense").value(31000.00))
                .andExpect(jsonPath("$.data.expenseBreakdown[0].name").value("\u8425\u9500"))
                .andExpect(jsonPath("$.data.topIncomeSources[0].name").value("\u4f01\u4e1a\u5e74\u6846\u9879\u76ee"));

        verify(dashboardService).getFinanceDashboard("last12months");
    }

    @Test
    void getHrDashboardShouldUseDefaultRangeAndReturnWrappedData() throws Exception {
        HrDashboardVO dashboard = new HrDashboardVO();
        dashboard.setActiveEmployeeCount(18L);
        dashboard.setActiveSalaryTotal(new BigDecimal("196000.00"));

        HrDashboardVO.DepartmentSalaryShareItem shareItem = new HrDashboardVO.DepartmentSalaryShareItem();
        shareItem.setDepartment("\u4ea7\u54c1\u90e8");
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
                .andExpect(jsonPath("$.data.departmentSalaryShare[0].department").value("\u4ea7\u54c1\u90e8"))
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
        structureItem.setTaxType("\u589e\u503c\u7a0e");
        structureItem.setAmount(new BigDecimal("26000.00"));
        structureItem.setRatio(new BigDecimal("0.6190"));
        dashboard.setTaxTypeStructure(List.of(structureItem));

        TaxDashboardVO.StatusSummaryItem summaryItem = new TaxDashboardVO.StatusSummaryItem();
        summaryItem.setStatus(0);
        summaryItem.setCount(2L);
        summaryItem.setAmount(new BigDecimal("9600.00"));
        dashboard.setStatusSummary(List.of(summaryItem));

        when(dashboardService.getTaxDashboard(null)).thenReturn(dashboard);

        mockMvc.perform(get("/api/dashboard/tax"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taxBurdenRate").value(0.0835))
                .andExpect(jsonPath("$.data.positiveTaxAmount").value(42000.00))
                .andExpect(jsonPath("$.data.taxTypeStructure[0].taxType").value("\u589e\u503c\u7a0e"))
                .andExpect(jsonPath("$.data.statusSummary[0].status").value(0));

        verify(dashboardService).getTaxDashboard(null);
    }
}
