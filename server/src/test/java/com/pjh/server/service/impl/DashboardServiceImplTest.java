package com.pjh.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pjh.server.config.AiProperties;
import com.pjh.server.entity.Company;
import com.pjh.server.entity.Employee;
import com.pjh.server.entity.FinanceRecord;
import com.pjh.server.entity.TaxRecord;
import com.pjh.server.mapper.CompanyMapper;
import com.pjh.server.mapper.EmployeeMapper;
import com.pjh.server.mapper.FinanceRecordMapper;
import com.pjh.server.mapper.TaxRecordMapper;
import com.pjh.server.mapper.UserMapper;
import com.pjh.server.service.HomeAiSummarySnapshotService;
import com.pjh.server.util.CurrentSessionService;
import com.pjh.server.vo.FinanceDashboardVO;
import com.pjh.server.vo.HomeAiSummaryVO;
import com.pjh.server.vo.HomeDashboardVO;
import com.pjh.server.vo.HrDashboardVO;
import com.pjh.server.vo.TaxDashboardVO;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock
    private FinanceRecordMapper financeRecordMapper;

    @Mock
    private EmployeeMapper employeeMapper;

    @Mock
    private TaxRecordMapper taxRecordMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CompanyMapper companyMapper;

    @Mock
    private CurrentSessionService currentSessionService;

    @Mock
    private ObjectProvider<OpenAiChatModel> chatModelProvider;

    @Mock
    private HomeAiSummarySnapshotService homeAiSummarySnapshotService;

    private DashboardServiceImpl dashboardService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-11T08:00:00Z"), ZoneId.of("Asia/Shanghai"));
        AiProperties aiProperties = new AiProperties();
        dashboardService = new DashboardServiceImpl(
                financeRecordMapper,
                employeeMapper,
                taxRecordMapper,
                userMapper,
                companyMapper,
                currentSessionService,
                fixedClock,
                aiProperties,
                chatModelProvider,
                homeAiSummarySnapshotService
        );
    }

    @Test
    void getHomeDashboardShouldUseCurrentMonthSummaryAndComputeNetProfit() {
        when(financeRecordMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(
                        List.of(
                                row("type", "income", "total", new BigDecimal("12000.00")),
                                row("type", "expense", "total", new BigDecimal("4800.00"))
                        ),
                        List.of()
                );
        when(financeRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(employeeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                employee(1L, "Alice", "研发部", "10000.00", "2025-11-05"),
                employee(2L, "Bob", "研发部", "8000.00", "2026-02-10"),
                employee(3L, "Cara", "市场部", "7000.00", "2026-01-12")
        ));
        when(taxRecordMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(row("total", BigDecimal.ZERO)));
        when(taxRecordMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(9L);

        HomeDashboardVO result = dashboardService.getHomeDashboard();

        assertEquals(0, result.getTotalIncome().compareTo(new BigDecimal("12000.00")));
        assertEquals(0, result.getTotalExpense().compareTo(new BigDecimal("4800.00")));
        assertEquals(0, result.getNetProfit().compareTo(new BigDecimal("7200.00")));
        assertFalse(result.isHasUnpaidWarning());
        assertTrue(result.getSetupStatus().isHasStaffAccount());
        assertFalse(result.getSetupStatus().isHasFinanceRecord());
        assertEquals(List.of("研发部", "市场部"),
                result.getDepartmentHeadcount().stream().map(HomeDashboardVO.DepartmentHeadcountItem::getDepartment).toList());
        assertEquals(List.of(2L, 1L),
                result.getDepartmentHeadcount().stream().map(HomeDashboardVO.DepartmentHeadcountItem::getEmployeeCount).toList());

        ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(financeRecordMapper, times(2)).selectMaps(captor.capture());
        QueryWrapper<?> summaryWrapper = captor.getAllValues().stream()
                .filter(wrapper -> {
                    String sqlSelect = String.valueOf(wrapper.getSqlSelect());
                    return sqlSelect.contains("SUM(amount)") && !sqlSelect.contains("CASE WHEN");
                })
                .findFirst()
                .orElseThrow();

        assertTrue(summaryWrapper.getSqlSegment().contains("date"));
        assertEquals(2, summaryWrapper.getParamNameValuePairs().size());
    }

    @Test
    void getHomeDashboardShouldOnlyWarnWhenPositiveUnpaidTaxExists() {
        when(financeRecordMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(), List.of());
        when(financeRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);
        when(employeeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(taxRecordMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(row("total", new BigDecimal("3200.00"))));
        when(taxRecordMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(9L);

        HomeDashboardVO result = dashboardService.getHomeDashboard();

        assertEquals(0, result.getUnpaidTax().compareTo(new BigDecimal("3200.00")));
        assertTrue(result.isHasUnpaidWarning());
        assertFalse(result.getSetupStatus().isHasStaffAccount());
        assertTrue(result.getSetupStatus().isHasFinanceRecord());

        ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(taxRecordMapper).selectMaps(captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("payment_status"));
        assertTrue(sqlSegment.contains("tax_amount"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(0));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(BigDecimal.ZERO));
    }

    @Test
    void getHomeDashboardShouldFillMissingMonthsAndCalculateProfit() {
        when(financeRecordMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(
                        List.of(),
                        List.of(
                                row("month", "2025-10", "income", new BigDecimal("1000.00"), "expense", new BigDecimal("200.00")),
                                row("month", "2026-02", "income", new BigDecimal("500.00"), "expense", new BigDecimal("800.00"))
                        )
                );
        when(financeRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(employeeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                employee(1L, "Alice", "研发部", "10000.00", "2025-11-05"),
                employee(2L, "Bob", "", "8000.00", "2026-02-10")
        ));
        when(taxRecordMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(row("total", BigDecimal.ZERO)));
        when(taxRecordMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(9L);

        HomeDashboardVO result = dashboardService.getHomeDashboard();

        assertEquals(6, result.getMonthlyTrend().size());
        assertEquals("2025-10", result.getMonthlyTrend().getFirst().getMonth());
        assertEquals(0, result.getMonthlyTrend().getFirst().getProfit().compareTo(new BigDecimal("800.00")));
        assertEquals("2025-11", result.getMonthlyTrend().get(1).getMonth());
        assertEquals(0, result.getMonthlyTrend().get(1).getIncome().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.getMonthlyTrend().get(1).getExpense().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.getMonthlyTrend().get(1).getProfit().compareTo(BigDecimal.ZERO));
        assertEquals("2026-02", result.getMonthlyTrend().get(4).getMonth());
        assertEquals(0, result.getMonthlyTrend().get(4).getIncome().compareTo(new BigDecimal("500.00")));
        assertEquals(0, result.getMonthlyTrend().get(4).getExpense().compareTo(new BigDecimal("800.00")));
        assertEquals(0, result.getMonthlyTrend().get(4).getProfit().compareTo(new BigDecimal("-300.00")));
        assertEquals("2026-03", result.getMonthlyTrend().getLast().getMonth());
        assertEquals(0, result.getMonthlyTrend().getLast().getProfit().compareTo(BigDecimal.ZERO));
        assertEquals(List.of("研发部", "未分配部门"),
                result.getDepartmentHeadcount().stream().map(HomeDashboardVO.DepartmentHeadcountItem::getDepartment).toList());
    }

    @Test
    void getHomeDashboardShouldSortTaxCalendarByResolvedPeriodAndId() {
        when(financeRecordMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(), List.of());
        when(financeRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(employeeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(taxRecordMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(row("total", BigDecimal.ZERO)));
        when(taxRecordMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(
                        taxRecord(1L, "2025-Annual", "企业所得税", 2, "0.00"),
                        taxRecord(2L, "2026-Q1", "增值税", 0, "3200.00"),
                        taxRecord(3L, "2026-03", "附加税", 1, "1200.00"),
                        taxRecord(4L, "2026-Q2", "企业所得税", 0, "4500.00")
                ));
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(9L);

        HomeDashboardVO result = dashboardService.getHomeDashboard();

        assertEquals(List.of("2026-Q2", "2026-03", "2026-Q1", "2025-Annual"),
                result.getTaxCalendar().stream().map(HomeDashboardVO.TaxCalendarItem::getTaxPeriod).toList());
    }

    @Test
    void getHomeAiSummaryShouldDelegateToSnapshotService() {
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(9L);
        HomeAiSummaryVO summary = new HomeAiSummaryVO();
        summary.setSummaryLines(List.of("旧摘要依然可用。"));
        summary.setGeneratedAt("2026-04-11T16:00");
        summary.setStatus("ready");
        when(homeAiSummarySnapshotService.getHomeAiSummary(9L)).thenReturn(summary);

        HomeAiSummaryVO result = dashboardService.getHomeAiSummary();

        assertEquals(summary, result);
        verify(homeAiSummarySnapshotService).getHomeAiSummary(eq(9L));
    }

    @Test
    void getFinanceDashboardShouldAggregateExpenseBreakdownAndTopIncomeSources() {
        when(financeRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(
                        List.of(
                                financeRecord(1L, "income", "16000.00", "软件", null, "2026-03-18"),
                                financeRecord(2L, "income", "12000.00", null, null, "2026-02-12"),
                                financeRecord(3L, "income", "9000.00", "代运营", "渠道合作", "2025-11-10"),
                                financeRecord(4L, "income", "7000.00", "培训", null, "2025-08-20"),
                                financeRecord(5L, "income", "5000.00", "顾问", "顾问包", "2025-06-15"),
                                financeRecord(6L, "income", "4000.00", "外包", null, "2025-05-08"),
                                financeRecord(7L, "income", "3000.00", "订阅", "长期客户", "2025-04-06"),
                                financeRecord(8L, "expense", "5000.00", "", null, "2026-03-02"),
                                financeRecord(9L, "expense", "2000.00", "人工", null, "2025-12-02"),
                                financeRecord(10L, "expense", "3000.00", "营销", null, "2025-04-01")
                        ),
                        List.of(
                                financeRecord(11L, "income", "6000.00", "咨询", null, "2024-12-12"),
                                financeRecord(12L, "expense", "1500.00", "差旅", null, "2025-01-12")
                        )
                );

        FinanceDashboardVO result = dashboardService.getFinanceDashboard("last12months");

        assertEquals(0, result.getTotalIncome().compareTo(new BigDecimal("56000.00")));
        assertEquals(0, result.getTotalExpense().compareTo(new BigDecimal("10000.00")));
        assertEquals(List.of("未分类支出", "营销", "人工"),
                result.getExpenseBreakdown().stream().map(FinanceDashboardVO.ExpenseBreakdownItem::getName).toList());
        assertEquals(0, result.getExpenseBreakdown().get(1).getRatio().compareTo(new BigDecimal("0.3000")));
        assertEquals(5, result.getTopIncomeSources().size());
        assertEquals(List.of("软件", "未标注来源", "渠道合作", "培训", "顾问包"),
                result.getTopIncomeSources().stream().map(FinanceDashboardVO.TopIncomeSourceItem::getName).toList());
        assertEquals(12, result.getMonthlyTrend().size());
        assertEquals("2025-04", result.getMonthlyTrend().getFirst().getMonth());
        assertEquals(0, result.getMonthlyTrend().getFirst().getProfit().compareTo(BigDecimal.ZERO));
        assertEquals("2025-07", result.getMonthlyTrend().get(3).getMonth());
        assertEquals(0, result.getMonthlyTrend().get(3).getProfit().compareTo(BigDecimal.ZERO));
        assertEquals("2026-03", result.getMonthlyTrend().getLast().getMonth());
        assertEquals(0, result.getMonthlyTrend().getLast().getProfit().compareTo(new BigDecimal("11000.00")));
        assertEquals(7, result.getIncomeConcentration().getSourceCount());
        assertEquals(0, result.getIncomeConcentration().getTop3Share().compareTo(new BigDecimal("0.6607")));
        assertEquals(0, result.getIncomeConcentration().getTop5Share().compareTo(new BigDecimal("0.8750")));
        assertEquals(0, result.getIncomeConcentration().getOtherShare().compareTo(new BigDecimal("0.1250")));
        assertEquals("较前12个月", result.getPeriodComparison().getBaselineLabel());
        assertEquals(0, result.getPeriodComparison().getIncomeChange().compareTo(new BigDecimal("50000.00")));
        assertEquals(0, result.getPeriodComparison().getExpenseChange().compareTo(new BigDecimal("8500.00")));
        assertEquals(0, result.getPeriodComparison().getProfitChange().compareTo(new BigDecimal("41500.00")));

        verify(financeRecordMapper, times(2)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void getFinanceDashboardShouldReturnFullHistoryTrendAndNullComparisonForAllRange() {
        when(financeRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        financeRecord(1L, "income", "10000.00", "咨询", "A项目", "2025-01-10"),
                        financeRecord(2L, "expense", "2000.00", "营销", null, "2025-01-12"),
                        financeRecord(3L, "income", "8000.00", "订阅", null, "2025-03-08"),
                        financeRecord(4L, "expense", "1000.00", "人工", null, "2025-04-09")
                ));

        FinanceDashboardVO result = dashboardService.getFinanceDashboard("all");

        assertNull(result.getPeriodComparison());
        assertEquals(List.of("2025-01", "2025-02", "2025-03", "2025-04"),
                result.getMonthlyTrend().stream().map(FinanceDashboardVO.MonthlyTrendItem::getMonth).toList());
        assertEquals(0, result.getMonthlyTrend().get(1).getProfit().compareTo(BigDecimal.ZERO));
        verify(financeRecordMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void getHrDashboardShouldBuildDepartmentShareAndMonthlyTrendFromActiveEmployees() {
        when(employeeMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        employee(1L, "Alice", "产品部", "10000.00", "2025-11-05"),
                        employee(2L, "Bob", "", "8000.00", "2026-02-10"),
                        employee(3L, "Charlie", "研发部", "12000.00", "2025-06-18")
                ));

        HrDashboardVO result = dashboardService.getHrDashboard("last6months");

        assertEquals(3L, result.getActiveEmployeeCount());
        assertEquals(0, result.getActiveSalaryTotal().compareTo(new BigDecimal("30000.00")));
        assertEquals(List.of("研发部", "产品部", "未分配部门"),
                result.getDepartmentSalaryShare().stream().map(HrDashboardVO.DepartmentSalaryShareItem::getDepartment).toList());
        assertEquals(6, result.getMonthlyTrend().size());
        assertEquals("2025-11", result.getMonthlyTrend().getFirst().getMonth());
        assertEquals(2L, result.getMonthlyTrend().getFirst().getEmployeeCount());
        assertEquals(0, result.getMonthlyTrend().getFirst().getSalaryAmount().compareTo(new BigDecimal("22000.00")));
        assertEquals("2026-02", result.getMonthlyTrend().get(3).getMonth());
        assertEquals(3L, result.getMonthlyTrend().get(3).getEmployeeCount());
        assertEquals(0, result.getMonthlyTrend().getLast().getSalaryAmount().compareTo(new BigDecimal("30000.00")));
    }

    @Test
    void getTaxDashboardShouldFilterPeriodsAndComputeTaxHealthMetrics() {
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(4L);
        when(taxRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        taxRecord(1L, "2026-Q1", "增值税", 0, "3200.00"),
                        taxRecord(2L, "2026-03", "附加税", 1, "1200.00"),
                        taxRecord(3L, "2026-Annual", "企业所得税", 2, "4100.00"),
                        taxRecord(4L, "2026-02", "个税退税", 1, "-600.00"),
                        taxRecord(5L, "2025-Annual", "企业所得税", 0, "9000.00")
                ), List.of(
                        taxRecord(1L, "2026-Q1", "增值税", 0, "3200.00"),
                        taxRecord(2L, "2026-03", "附加税", 1, "1200.00"),
                        taxRecord(3L, "2026-Annual", "企业所得税", 2, "4100.00"),
                        taxRecord(4L, "2026-02", "个税退税", 1, "-600.00"),
                        taxRecord(5L, "2025-Annual", "企业所得税", 0, "9000.00")
                ));
        when(financeRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        financeRecord(1L, "income", "100000.00", "软件", "年度合同", "2026-01-08"),
                        financeRecord(2L, "income", "25000.00", "咨询", null, "2026-03-18"),
                        financeRecord(3L, "expense", "5000.00", "营销", null, "2026-03-20")
                ), List.of(
                        financeRecord(4L, "income", "100000.00", "软件", "上一年合同", "2025-02-08"),
                        financeRecord(5L, "expense", "8000.00", "营销", null, "2025-03-20")
                ));

        TaxDashboardVO result = dashboardService.getTaxDashboard("2026");

        assertEquals(0, result.getPositiveTaxAmount().compareTo(new BigDecimal("8500.00")));
        assertEquals(0, result.getIncomeBase().compareTo(new BigDecimal("125000.00")));
        assertEquals(0, result.getUnpaidTaxAmount().compareTo(new BigDecimal("3200.00")));
        assertEquals(0, result.getTaxBurdenRate().compareTo(new BigDecimal("0.0680")));
        assertEquals(List.of(2026, 2025), result.getAvailableYears());
        assertEquals(2026, result.getSelectedYear());
        assertEquals(List.of("企业所得税", "增值税", "附加税"),
                result.getTaxTypeStructure().stream().map(TaxDashboardVO.TaxTypeStructureItem::getTaxType).toList());
        assertEquals(3, result.getStatusSummary().size());
        assertEquals(0, result.getStatusSummary().get(0).getAmount().compareTo(new BigDecimal("3200.00")));
        assertEquals(0, result.getStatusSummary().get(1).getAmount().compareTo(new BigDecimal("600.00")));
        assertEquals(0, result.getStatusSummary().get(2).getAmount().compareTo(new BigDecimal("4100.00")));
        assertEquals("较2025年度", result.getPeriodComparison().getBaselineLabel());
        assertEquals(0, result.getPeriodComparison().getPreviousTaxBurdenRate().compareTo(new BigDecimal("0.0900")));
        assertEquals(0, result.getPeriodComparison().getBurdenRateDelta().compareTo(new BigDecimal("-0.0220")));
        assertEquals(0, result.getPeriodComparison().getPreviousUnpaidTaxAmount().compareTo(new BigDecimal("9000.00")));
        assertEquals(0, result.getPeriodComparison().getUnpaidTaxAmountDelta().compareTo(new BigDecimal("-5800.00")));
        assertEquals(0, result.getPeriodComparison().getPreviousPositiveTaxAmount().compareTo(new BigDecimal("9000.00")));
        assertEquals(0, result.getPeriodComparison().getPositiveTaxAmountDelta().compareTo(new BigDecimal("-500.00")));
        assertEquals(1, result.getRecentOutstanding().size());
        assertEquals("2026-Q1", result.getRecentOutstanding().getFirst().getTaxPeriod());
        assertEquals("增值税", result.getRecentOutstanding().getFirst().getTaxType());
        assertEquals(0, result.getRecentOutstanding().getFirst().getAmount().compareTo(new BigDecimal("3200.00")));

        ArgumentCaptor<LambdaQueryWrapper> taxWrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        ArgumentCaptor<LambdaQueryWrapper> financeWrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(taxRecordMapper, times(2)).selectList(taxWrapperCaptor.capture());
        verify(financeRecordMapper, times(2)).selectList(financeWrapperCaptor.capture());
    }

    @Test
    void getTaxDashboardShouldReturnZeroBurdenWhenNoPositiveIncomeBaseExists() {
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(4L);
        when(taxRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(
                        List.of(taxRecord(1L, "2026-Q1", "增值税", 0, "3200.00")),
                        List.of()
                );
        when(financeRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        financeRecord(1L, "expense", "6800.00", "采购", null, "2026-02-10"),
                        financeRecord(2L, "income", "-1000.00", "异常", null, "2026-03-12")
                ), List.of());

        TaxDashboardVO result = dashboardService.getTaxDashboard("2026");

        assertEquals(0, result.getIncomeBase().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.getTaxBurdenRate().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.getPeriodComparison().getPreviousTaxBurdenRate().compareTo(BigDecimal.ZERO));
        assertEquals(2026, result.getSelectedYear());
    }

    @Test
    void getTaxDashboardShouldReturnNullComparisonForAllRangeAndLimitOutstandingItems() {
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(4L);
        when(taxRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        taxRecord(1L, "2026-04", "增值税", 0, "1200.00"),
                        taxRecord(2L, "2026-03", "企业所得税", 0, "3100.00"),
                        taxRecord(3L, "2026-03", "附加税", 0, "900.00"),
                        taxRecord(4L, "2026-Q1", "印花税", 0, "600.00"),
                        taxRecord(5L, "2026-Q1", "个人所得税", 0, "1800.00"),
                        taxRecord(6L, "2025-Annual", "企业所得税", 0, "2700.00"),
                        taxRecord(7L, "2025-12", "增值税", 1, "1500.00"),
                        taxRecord(8L, "2025-11", "退税调整", 1, "-300.00")
                ));
        when(financeRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        financeRecord(1L, "income", "50000.00", "软件", "年度服务", "2025-11-15"),
                        financeRecord(2L, "income", "60000.00", "软件", "年度服务", "2026-03-15")
                ));

        TaxDashboardVO result = dashboardService.getTaxDashboard("all");

        assertNull(result.getPeriodComparison());
        assertNull(result.getSelectedYear());
        assertEquals(List.of(2026, 2025), result.getAvailableYears());
        assertEquals(5, result.getRecentOutstanding().size());
        assertEquals(List.of("2026-04", "2026-03", "2026-03", "2026-Q1", "2026-Q1"),
                result.getRecentOutstanding().stream().map(TaxDashboardVO.OutstandingItem::getTaxPeriod).toList());
        assertEquals(List.of("增值税", "企业所得税", "附加税", "个人所得税", "印花税"),
                result.getRecentOutstanding().stream().map(TaxDashboardVO.OutstandingItem::getTaxType).toList());
    }

    @Test
    void getTaxDashboardShouldResolveBlankRangeToLatestAvailableYear() {
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(4L);
        when(taxRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        taxRecord(1L, "2025-Annual", "企业所得税", 0, "9000.00"),
                        taxRecord(2L, "2024-Annual", "企业所得税", 1, "7000.00")
                ), List.of(
                        taxRecord(1L, "2025-Annual", "企业所得税", 0, "9000.00"),
                        taxRecord(2L, "2024-Annual", "企业所得税", 1, "7000.00")
                ));
        when(financeRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        financeRecord(1L, "income", "120000.00", "软件", "年度服务", "2025-06-15")
                ), List.of(
                        financeRecord(2L, "income", "100000.00", "软件", "上一年度服务", "2024-06-15")
                ));

        TaxDashboardVO result = dashboardService.getTaxDashboard("");

        assertEquals(List.of(2025, 2024), result.getAvailableYears());
        assertEquals(2025, result.getSelectedYear());
        assertEquals("较2024年度", result.getPeriodComparison().getBaselineLabel());
        assertEquals(0, result.getTaxBurdenRate().compareTo(new BigDecimal("0.0750")));
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            row.put(String.valueOf(values[i]), values[i + 1]);
        }
        return row;
    }

    private FinanceRecord financeRecord(
            Long id,
            String type,
            String amount,
            String category,
            String project,
            String date
    ) {
        FinanceRecord record = new FinanceRecord();
        record.setId(id);
        record.setType(type);
        record.setAmount(new BigDecimal(amount));
        record.setCategory(category);
        record.setProject(project);
        record.setDate(LocalDate.parse(date));
        return record;
    }

    private Employee employee(
            Long id,
            String name,
            String department,
            String salary,
            String hireDate
    ) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setName(name);
        employee.setDepartment(department);
        employee.setSalary(new BigDecimal(salary));
        employee.setHireDate(LocalDate.parse(hireDate));
        employee.setStatus(1);
        return employee;
    }

    private TaxRecord taxRecord(Long id, String taxPeriod, String taxType, Integer status, String amount) {
        TaxRecord record = new TaxRecord();
        record.setId(id);
        record.setTaxPeriod(taxPeriod);
        record.setTaxType(taxType);
        record.setPaymentStatus(status);
        record.setTaxAmount(new BigDecimal(amount));
        return record;
    }

    private Company company() {
        Company company = new Company();
        company.setId(9L);
        company.setName("星桥供应链");
        company.setIndustry("跨境贸易");
        company.setTaxpayerType("一般纳税人");
        company.setDescription("主营供应链服务");
        return company;
    }
}
