package com.pjh.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pjh.server.entity.Employee;
import com.pjh.server.entity.FinanceRecord;
import com.pjh.server.entity.TaxRecord;
import com.pjh.server.mapper.EmployeeMapper;
import com.pjh.server.mapper.FinanceRecordMapper;
import com.pjh.server.mapper.TaxRecordMapper;
import com.pjh.server.vo.FinanceDashboardVO;
import com.pjh.server.vo.HomeDashboardVO;
import com.pjh.server.vo.HrDashboardVO;
import com.pjh.server.vo.TaxDashboardVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private DashboardServiceImpl dashboardService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-11T08:00:00Z"), ZoneId.of("Asia/Shanghai"));
        dashboardService = new DashboardServiceImpl(financeRecordMapper, employeeMapper, taxRecordMapper, fixedClock);
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
        when(taxRecordMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(row("total", BigDecimal.ZERO)));
        when(taxRecordMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        HomeDashboardVO result = dashboardService.getHomeDashboard();

        assertEquals(0, result.getTotalIncome().compareTo(new BigDecimal("12000.00")));
        assertEquals(0, result.getTotalExpense().compareTo(new BigDecimal("4800.00")));
        assertEquals(0, result.getNetProfit().compareTo(new BigDecimal("7200.00")));
        assertFalse(result.isHasUnpaidWarning());

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
        when(taxRecordMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(row("total", new BigDecimal("3200.00"))));
        when(taxRecordMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        HomeDashboardVO result = dashboardService.getHomeDashboard();

        assertEquals(0, result.getUnpaidTax().compareTo(new BigDecimal("3200.00")));
        assertTrue(result.isHasUnpaidWarning());

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
                                row("month", "2025-11", "income", new BigDecimal("1000.00"), "expense", new BigDecimal("200.00")),
                                row("month", "2026-02", "income", new BigDecimal("500.00"), "expense", new BigDecimal("800.00"))
                        )
                );
        when(taxRecordMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(row("total", BigDecimal.ZERO)));
        when(taxRecordMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        HomeDashboardVO result = dashboardService.getHomeDashboard();

        assertEquals(6, result.getMonthlyTrend().size());
        assertEquals("2025-11", result.getMonthlyTrend().getFirst().getMonth());
        assertEquals(0, result.getMonthlyTrend().getFirst().getProfit().compareTo(new BigDecimal("800.00")));
        assertEquals("2025-12", result.getMonthlyTrend().get(1).getMonth());
        assertEquals(0, result.getMonthlyTrend().get(1).getIncome().compareTo(BigDecimal.ZERO));
        assertEquals("2026-02", result.getMonthlyTrend().get(3).getMonth());
        assertEquals(0, result.getMonthlyTrend().get(3).getProfit().compareTo(new BigDecimal("-300.00")));
        assertEquals("2026-04", result.getMonthlyTrend().getLast().getMonth());
    }

    @Test
    void getHomeDashboardShouldSortTaxCalendarByResolvedPeriodAndId() {
        when(financeRecordMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(), List.of());
        when(taxRecordMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(row("total", BigDecimal.ZERO)));
        when(taxRecordMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(
                        taxRecord(1L, "2025-Annual", "企业所得税", 2, "0.00"),
                        taxRecord(2L, "2026-Q1", "增值税", 0, "3200.00"),
                        taxRecord(3L, "2026-03", "附加税", 1, "1200.00"),
                        taxRecord(4L, "2026-Q2", "企业所得税", 0, "4500.00")
                ));

        HomeDashboardVO result = dashboardService.getHomeDashboard();

        assertEquals(List.of("2026-Q2", "2026-03", "2026-Q1", "2025-Annual"),
                result.getTaxCalendar().stream().map(HomeDashboardVO.TaxCalendarItem::getTaxPeriod).toList());
    }

    @Test
    void getFinanceDashboardShouldAggregateExpenseBreakdownAndTopIncomeSources() {
        when(financeRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        financeRecord(1L, "income", "18000.00", "咨询", "企业年框项目", "2026-04-05"),
                        financeRecord(2L, "income", "16000.00", "软件", null, "2026-03-18"),
                        financeRecord(3L, "income", "12000.00", null, null, "2026-02-12"),
                        financeRecord(4L, "income", "9000.00", "代运营", "渠道合作", "2025-11-10"),
                        financeRecord(5L, "income", "7000.00", "培训", null, "2025-08-20"),
                        financeRecord(6L, "income", "5000.00", "顾问", "顾问包", "2025-06-15"),
                        financeRecord(7L, "income", "4000.00", "外包", null, "2025-05-08"),
                        financeRecord(8L, "expense", "8000.00", "营销", null, "2026-04-02"),
                        financeRecord(9L, "expense", "5000.00", "", null, "2026-03-02"),
                        financeRecord(10L, "expense", "2000.00", "人工", null, "2025-12-02")
                ));

        FinanceDashboardVO result = dashboardService.getFinanceDashboard("last12months");

        assertEquals(0, result.getTotalIncome().compareTo(new BigDecimal("71000.00")));
        assertEquals(0, result.getTotalExpense().compareTo(new BigDecimal("15000.00")));
        assertEquals(List.of("营销", "未分类支出", "人工"),
                result.getExpenseBreakdown().stream().map(FinanceDashboardVO.ExpenseBreakdownItem::getName).toList());
        assertEquals(0, result.getExpenseBreakdown().get(1).getRatio().compareTo(new BigDecimal("0.3333")));
        assertEquals(5, result.getTopIncomeSources().size());
        assertEquals(List.of("企业年框项目", "软件", "未标注来源", "渠道合作", "培训"),
                result.getTopIncomeSources().stream().map(FinanceDashboardVO.TopIncomeSourceItem::getName).toList());
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
        when(taxRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
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
                ));

        TaxDashboardVO result = dashboardService.getTaxDashboard("thisYear");

        assertEquals(0, result.getPositiveTaxAmount().compareTo(new BigDecimal("8500.00")));
        assertEquals(0, result.getIncomeBase().compareTo(new BigDecimal("125000.00")));
        assertEquals(0, result.getUnpaidTaxAmount().compareTo(new BigDecimal("3200.00")));
        assertEquals(0, result.getTaxBurdenRate().compareTo(new BigDecimal("0.0680")));
        assertEquals(List.of("企业所得税", "增值税", "附加税"),
                result.getTaxTypeStructure().stream().map(TaxDashboardVO.TaxTypeStructureItem::getTaxType).toList());
        assertEquals(3, result.getStatusSummary().size());
        assertEquals(0, result.getStatusSummary().get(0).getAmount().compareTo(new BigDecimal("3200.00")));
        assertEquals(0, result.getStatusSummary().get(1).getAmount().compareTo(new BigDecimal("600.00")));
        assertEquals(0, result.getStatusSummary().get(2).getAmount().compareTo(new BigDecimal("4100.00")));
    }

    @Test
    void getTaxDashboardShouldReturnZeroBurdenWhenNoPositiveIncomeBaseExists() {
        when(taxRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(taxRecord(1L, "2026-Q1", "增值税", 0, "3200.00")));
        when(financeRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        financeRecord(1L, "expense", "6800.00", "采购", null, "2026-02-10"),
                        financeRecord(2L, "income", "-1000.00", "异常", null, "2026-03-12")
                ));

        TaxDashboardVO result = dashboardService.getTaxDashboard("thisYear");

        assertEquals(0, result.getIncomeBase().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.getTaxBurdenRate().compareTo(BigDecimal.ZERO));
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
}
