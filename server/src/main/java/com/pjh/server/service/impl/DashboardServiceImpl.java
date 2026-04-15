package com.pjh.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.pjh.server.config.AiProperties;
import com.pjh.server.common.Constants;
import com.pjh.server.entity.Company;
import com.pjh.server.entity.Employee;
import com.pjh.server.entity.FinanceRecord;
import com.pjh.server.entity.TaxRecord;
import com.pjh.server.entity.User;
import com.pjh.server.exception.BusinessException;
import com.pjh.server.mapper.CompanyMapper;
import com.pjh.server.mapper.EmployeeMapper;
import com.pjh.server.mapper.FinanceRecordMapper;
import com.pjh.server.mapper.TaxRecordMapper;
import com.pjh.server.mapper.UserMapper;
import com.pjh.server.service.DashboardService;
import com.pjh.server.util.CurrentSessionService;
import com.pjh.server.vo.FinanceDashboardVO;
import com.pjh.server.vo.HomeAiSummaryVO;
import com.pjh.server.vo.HomeDashboardVO;
import com.pjh.server.vo.HrDashboardVO;
import com.pjh.server.vo.TaxDashboardVO;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int HOME_TREND_MONTH_COUNT = 6;
    private static final Duration HOME_AI_SUMMARY_CACHE_TTL = Duration.ofMinutes(10);
    private static final Set<String> FINANCE_AND_HR_RANGES = Set.of("last3months", "last6months", "last12months", "all");
    private static final Set<String> TAX_LEGACY_RANGES = Set.of("thisYear", "last12months", "all");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Pattern YEAR_RANGE_PATTERN = Pattern.compile("^(\\d{4})$");
    private static final Pattern MONTH_PERIOD_PATTERN = Pattern.compile("^(\\d{4})-(0[1-9]|1[0-2])$");
    private static final Pattern QUARTER_PERIOD_PATTERN = Pattern.compile("^(\\d{4})-Q([1-4])$");
    private static final Pattern ANNUAL_PERIOD_PATTERN = Pattern.compile("^(\\d{4})-Annual$");

    private final FinanceRecordMapper financeRecordMapper;
    private final EmployeeMapper employeeMapper;
    private final TaxRecordMapper taxRecordMapper;
    private final UserMapper userMapper;
    private final CompanyMapper companyMapper;
    private final CurrentSessionService currentSessionService;
    private final Clock clock;
    private final AiProperties aiProperties;
    private final ObjectProvider<OpenAiChatModel> chatModelProvider;
    private final Cache<Long, HomeAiSummaryVO> homeAiSummaryCache = Caffeine.newBuilder()
            .expireAfterWrite(HOME_AI_SUMMARY_CACHE_TTL)
            .maximumSize(128)
            .build();

    @Override
    public HomeDashboardVO getHomeDashboard() {
        LocalDate today = LocalDate.now(clock);
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate monthStart = currentMonth.atDay(1);
        LocalDate monthEnd = currentMonth.atEndOfMonth();
        List<Employee> activeEmployees = queryActiveEmployees();

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        for (Map<String, Object> row : queryCurrentMonthSummary(monthStart, monthEnd)) {
            String type = getString(row, "type");
            BigDecimal total = getBigDecimal(row, "total");
            if ("income".equals(type)) {
                totalIncome = total;
            } else if ("expense".equals(type)) {
                totalExpense = total;
            }
        }

        BigDecimal unpaidTax = queryUnpaidTax();

        HomeDashboardVO result = new HomeDashboardVO();
        result.setTotalIncome(totalIncome);
        result.setTotalExpense(totalExpense);
        result.setNetProfit(totalIncome.subtract(totalExpense));
        result.setUnpaidTax(unpaidTax);
        result.setHasUnpaidWarning(unpaidTax.compareTo(BigDecimal.ZERO) > 0);
        result.setMonthlyTrend(buildMonthlyTrend(currentMonth.minusMonths(1)));
        result.setDepartmentHeadcount(buildHomeDepartmentHeadcount(activeEmployees));
        result.setTaxCalendar(buildTaxCalendar());
        result.setSetupStatus(buildSetupStatus());
        return result;
    }

    @Override
    public HomeAiSummaryVO getHomeAiSummary() {
        Long companyId = currentSessionService.requireCurrentCompanyId();
        HomeAiSummaryVO cached = homeAiSummaryCache.getIfPresent(companyId);
        if (cached != null) {
            return cached;
        }

        Company company = companyMapper.selectById(companyId);
        HomeDashboardVO dashboard = getHomeDashboard();

        HomeAiSummaryVO summary = new HomeAiSummaryVO();
        summary.setSummaryLines(generateHomeAiSummaryLines(companyId, company, dashboard));
        summary.setGeneratedAt(LocalDateTime.now(clock).toString());
        homeAiSummaryCache.put(companyId, summary);
        return summary;
    }

    @Override
    public FinanceDashboardVO getFinanceDashboard(String range) {
        String normalizedRange = normalizeRange(range);
        DateRange dateRange = resolveFinanceRange(normalizedRange);
        List<FinanceRecord> records = queryFinanceRecords(dateRange);
        FinanceAggregation aggregation = aggregateFinanceRecords(records);

        FinanceDashboardVO result = new FinanceDashboardVO();
        result.setTotalExpense(aggregation.totalExpense());
        result.setTotalIncome(aggregation.totalIncome());
        BigDecimal finalTotalExpense = aggregation.totalExpense();
        result.setExpenseBreakdown(aggregation.expenseMap().entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .map(entry -> toExpenseBreakdownItem(entry.getKey(), entry.getValue(), finalTotalExpense))
                .toList());
        result.setTopIncomeSources(aggregation.incomeMap().entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(5)
                .map(entry -> toTopIncomeSourceItem(entry.getKey(), entry.getValue()))
                .toList());
        result.setMonthlyTrend(buildFinanceMonthlyTrend(records, dateRange, normalizedRange));
        result.setIncomeConcentration(buildFinanceIncomeConcentration(aggregation.incomeMap(), aggregation.totalIncome()));
        result.setPeriodComparison(buildFinancePeriodComparison(normalizedRange, aggregation));
        return result;
    }

    @Override
    public HrDashboardVO getHrDashboard(String range) {
        List<Employee> activeEmployees = queryActiveEmployees();
        MonthRange monthRange = resolveHrMonthRange(range, activeEmployees);

        Map<String, DepartmentAccumulator> departmentMap = new LinkedHashMap<>();
        BigDecimal activeSalaryTotal = ZERO;

        for (Employee employee : activeEmployees) {
            BigDecimal salary = safeAmount(employee.getSalary());
            activeSalaryTotal = activeSalaryTotal.add(salary);

            DepartmentAccumulator accumulator = departmentMap.computeIfAbsent(
                    normalizeDepartment(employee.getDepartment()),
                    ignored -> new DepartmentAccumulator()
            );
            accumulator.employeeCount++;
            accumulator.salaryAmount = accumulator.salaryAmount.add(salary);
        }

        HrDashboardVO result = new HrDashboardVO();
        result.setActiveEmployeeCount((long) activeEmployees.size());
        result.setActiveSalaryTotal(activeSalaryTotal);
        BigDecimal finalActiveSalaryTotal = activeSalaryTotal;
        result.setDepartmentSalaryShare(departmentMap.entrySet().stream()
                .sorted((left, right) -> {
                    int compareAmount = right.getValue().salaryAmount.compareTo(left.getValue().salaryAmount);
                    if (compareAmount != 0) {
                        return compareAmount;
                    }
                    return left.getKey().compareTo(right.getKey());
                })
                .map(entry -> toDepartmentSalaryShareItem(entry.getKey(), entry.getValue(), finalActiveSalaryTotal))
                .toList());
        result.setMonthlyTrend(buildHrMonthlyTrend(activeEmployees, monthRange));
        return result;
    }

    @Override
    public TaxDashboardVO getTaxDashboard(String range) {
        String normalizedRange = normalizeRange(range);
        Long companyId = currentSessionService.requireCurrentCompanyId();
        List<TaxRecord> allRecords = queryTaxRecords(companyId);
        List<Integer> availableYears = resolveAvailableTaxYears(allRecords);
        Integer selectedYear = resolveSelectedTaxYear(normalizedRange, availableYears);
        DateRange financeRange = resolveTaxFinanceRange(normalizedRange, selectedYear);
        DateRange taxRange = resolveTaxPeriodRange(normalizedRange, selectedYear);
        List<TaxRecord> records = allRecords.stream()
                .filter(record -> isTaxRecordWithinRange(record, taxRange))
                .toList();

        Map<String, BigDecimal> taxTypeMap = new LinkedHashMap<>();
        Map<Integer, StatusAccumulator> statusMap = initStatusMap();
        BigDecimal positiveTaxAmount = ZERO;
        BigDecimal unpaidTaxAmount = ZERO;

        for (TaxRecord record : records) {
            BigDecimal amount = safeAmount(record.getTaxAmount());
            Integer status = record.getPaymentStatus();
            if (status == null) {
                continue;
            }

            StatusAccumulator accumulator = statusMap.computeIfAbsent(status, ignored -> new StatusAccumulator());
            accumulator.count++;
            accumulator.amount = accumulator.amount.add(amount);

            if (amount.compareTo(ZERO) > 0) {
                positiveTaxAmount = positiveTaxAmount.add(amount);
                taxTypeMap.merge(normalizeTaxType(record.getTaxType()), amount, BigDecimal::add);
                if (status != null && status == 0) {
                    unpaidTaxAmount = unpaidTaxAmount.add(amount);
                }
            }
        }

        BigDecimal incomeBase = buildTaxIncomeBase(financeRange, companyId);
        BigDecimal taxBurdenRate = safeDivide(positiveTaxAmount, incomeBase);

        TaxDashboardVO result = new TaxDashboardVO();
        result.setPositiveTaxAmount(positiveTaxAmount);
        result.setIncomeBase(incomeBase);
        result.setUnpaidTaxAmount(unpaidTaxAmount);
        result.setTaxBurdenRate(taxBurdenRate);
        result.setAvailableYears(availableYears);
        result.setSelectedYear(selectedYear);
        BigDecimal finalPositiveTaxAmount = positiveTaxAmount;
        result.setTaxTypeStructure(taxTypeMap.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .map(entry -> toTaxTypeStructureItem(entry.getKey(), entry.getValue(), finalPositiveTaxAmount))
                .toList());
        result.setStatusSummary(statusMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> toStatusSummaryItem(entry.getKey(), entry.getValue()))
                .toList());
        result.setPeriodComparison(buildTaxPeriodComparison(
                normalizedRange,
                companyId,
                new TaxComparisonSnapshot(positiveTaxAmount, unpaidTaxAmount, taxBurdenRate),
                selectedYear
        ));
        result.setRecentOutstanding(buildRecentOutstanding(records));
        return result;
    }

    private List<Map<String, Object>> queryCurrentMonthSummary(LocalDate monthStart, LocalDate monthEnd) {
        QueryWrapper<FinanceRecord> wrapper = new QueryWrapper<>();
        wrapper.select("type", "COALESCE(SUM(amount), 0) AS total")
                .ge("date", monthStart)
                .le("date", monthEnd)
                .groupBy("type");
        return financeRecordMapper.selectMaps(wrapper);
    }

    private BigDecimal queryUnpaidTax() {
        QueryWrapper<TaxRecord> wrapper = new QueryWrapper<>();
        wrapper.select("COALESCE(SUM(tax_amount), 0) AS total")
                .eq("payment_status", 0)
                .gt("tax_amount", BigDecimal.ZERO);

        List<Map<String, Object>> rows = taxRecordMapper.selectMaps(wrapper);
        if (rows == null || rows.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return getBigDecimal(rows.getFirst(), "total");
    }

    private HomeDashboardVO.SetupStatus buildSetupStatus() {
        Long companyId = currentSessionService.requireCurrentCompanyId();

        Long staffCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getCompanyId, companyId)
                        .eq(User::getRole, Constants.ROLE_STAFF)
        );
        Long financeRecordCount = financeRecordMapper.selectCount(new LambdaQueryWrapper<>());

        HomeDashboardVO.SetupStatus setupStatus = new HomeDashboardVO.SetupStatus();
        setupStatus.setHasStaffAccount(staffCount != null && staffCount > 0);
        setupStatus.setHasFinanceRecord(financeRecordCount != null && financeRecordCount > 0);
        return setupStatus;
    }

    private List<FinanceRecord> queryFinanceRecords(DateRange dateRange) {
        LambdaQueryWrapper<FinanceRecord> wrapper = new LambdaQueryWrapper<>();
        if (dateRange.start() != null) {
            wrapper.ge(FinanceRecord::getDate, dateRange.start());
        }
        if (dateRange.end() != null) {
            wrapper.le(FinanceRecord::getDate, dateRange.end());
        }
        wrapper.orderByAsc(FinanceRecord::getDate).orderByAsc(FinanceRecord::getId);
        return financeRecordMapper.selectList(wrapper);
    }

    private List<FinanceRecord> queryFinanceRecords(DateRange dateRange, Long companyId) {
        LambdaQueryWrapper<FinanceRecord> wrapper = new LambdaQueryWrapper<FinanceRecord>()
                .eq(FinanceRecord::getCompanyId, companyId);
        if (dateRange.start() != null) {
            wrapper.ge(FinanceRecord::getDate, dateRange.start());
        }
        if (dateRange.end() != null) {
            wrapper.le(FinanceRecord::getDate, dateRange.end());
        }
        wrapper.orderByAsc(FinanceRecord::getDate).orderByAsc(FinanceRecord::getId);
        return financeRecordMapper.selectList(wrapper);
    }

    private List<Employee> queryActiveEmployees() {
        Long companyId = currentSessionService.requireCurrentCompanyId();
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<Employee>()
                .eq(Employee::getCompanyId, companyId)
                .eq(Employee::getStatus, 1)
                .orderByAsc(Employee::getHireDate)
                .orderByAsc(Employee::getId);
        return employeeMapper.selectList(wrapper);
    }

    private List<TaxRecord> queryTaxRecords() {
        LambdaQueryWrapper<TaxRecord> wrapper = new LambdaQueryWrapper<TaxRecord>()
                .orderByAsc(TaxRecord::getId);
        return taxRecordMapper.selectList(wrapper);
    }

    private List<TaxRecord> queryTaxRecords(Long companyId) {
        LambdaQueryWrapper<TaxRecord> wrapper = new LambdaQueryWrapper<TaxRecord>()
                .eq(TaxRecord::getCompanyId, companyId)
                .orderByAsc(TaxRecord::getId);
        return taxRecordMapper.selectList(wrapper);
    }

    private List<HomeDashboardVO.MonthlyTrendPoint> buildMonthlyTrend(YearMonth endMonth) {
        YearMonth startMonth = endMonth.minusMonths(HOME_TREND_MONTH_COUNT - 1L);
        LocalDate startDate = startMonth.atDay(1);
        LocalDate endDate = endMonth.atEndOfMonth();

        QueryWrapper<FinanceRecord> wrapper = new QueryWrapper<>();
        wrapper.select(
                        "DATE_FORMAT(date, '%Y-%m') AS month",
                        "COALESCE(SUM(CASE WHEN type = 'income' THEN amount ELSE 0 END), 0) AS income",
                        "COALESCE(SUM(CASE WHEN type = 'expense' THEN amount ELSE 0 END), 0) AS expense"
                )
                .ge("date", startDate)
                .le("date", endDate)
                .groupBy("DATE_FORMAT(date, '%Y-%m')");

        List<Map<String, Object>> rows = financeRecordMapper.selectMaps(wrapper);
        Map<String, Map<String, Object>> rowMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            rowMap.put(getString(row, "month"), row);
        }

        List<HomeDashboardVO.MonthlyTrendPoint> trend = new ArrayList<>();
        for (int i = 0; i < HOME_TREND_MONTH_COUNT; i++) {
            YearMonth month = startMonth.plusMonths(i);
            String monthKey = month.format(MONTH_FORMATTER);
            Map<String, Object> row = rowMap.get(monthKey);
            BigDecimal income = row == null ? BigDecimal.ZERO : getBigDecimal(row, "income");
            BigDecimal expense = row == null ? BigDecimal.ZERO : getBigDecimal(row, "expense");

            HomeDashboardVO.MonthlyTrendPoint point = new HomeDashboardVO.MonthlyTrendPoint();
            point.setMonth(monthKey);
            point.setIncome(income);
            point.setExpense(expense);
            point.setProfit(income.subtract(expense));
            trend.add(point);
        }
        return trend;
    }

    private FinanceAggregation aggregateFinanceRecords(List<FinanceRecord> records) {
        Map<String, BigDecimal> expenseMap = new LinkedHashMap<>();
        Map<String, BigDecimal> incomeMap = new LinkedHashMap<>();
        BigDecimal totalExpense = ZERO;
        BigDecimal totalIncome = ZERO;

        for (FinanceRecord record : records) {
            BigDecimal amount = safeAmount(record.getAmount());
            if ("expense".equals(record.getType())) {
                totalExpense = totalExpense.add(amount);
                expenseMap.merge(normalizeFinanceCategory(record.getCategory()), amount, BigDecimal::add);
            } else if ("income".equals(record.getType())) {
                totalIncome = totalIncome.add(amount);
                incomeMap.merge(resolveIncomeSourceName(record), amount, BigDecimal::add);
            }
        }

        return new FinanceAggregation(totalIncome, totalExpense, incomeMap, expenseMap);
    }

    private List<FinanceDashboardVO.MonthlyTrendItem> buildFinanceMonthlyTrend(List<FinanceRecord> records,
                                                                                DateRange dateRange,
                                                                                String normalizedRange) {
        if ("all".equals(normalizedRange) && records.isEmpty()) {
            return List.of();
        }

        YearMonth startMonth;
        YearMonth endMonth;
        if ("all".equals(normalizedRange)) {
            LocalDate earliestDate = records.stream()
                    .map(FinanceRecord::getDate)
                    .filter(Objects::nonNull)
                    .min(LocalDate::compareTo)
                    .orElse(null);
            LocalDate latestDate = records.stream()
                    .map(FinanceRecord::getDate)
                    .filter(Objects::nonNull)
                    .max(LocalDate::compareTo)
                    .orElse(null);
            if (earliestDate == null || latestDate == null) {
                return List.of();
            }
            startMonth = YearMonth.from(earliestDate);
            endMonth = YearMonth.from(latestDate);
        } else {
            startMonth = YearMonth.from(dateRange.start());
            endMonth = YearMonth.from(dateRange.end());
        }

        Map<String, FinanceMonthAccumulator> monthMap = new LinkedHashMap<>();
        for (FinanceRecord record : records) {
            if (record.getDate() == null) {
                continue;
            }
            String monthKey = YearMonth.from(record.getDate()).format(MONTH_FORMATTER);
            FinanceMonthAccumulator accumulator = monthMap.computeIfAbsent(monthKey, ignored -> new FinanceMonthAccumulator());
            BigDecimal amount = safeAmount(record.getAmount());
            if ("income".equals(record.getType())) {
                accumulator.income = accumulator.income.add(amount);
            } else if ("expense".equals(record.getType())) {
                accumulator.expense = accumulator.expense.add(amount);
            }
        }

        List<FinanceDashboardVO.MonthlyTrendItem> trend = new ArrayList<>();
        YearMonth currentMonth = startMonth;
        while (!currentMonth.isAfter(endMonth)) {
            String monthKey = currentMonth.format(MONTH_FORMATTER);
            FinanceMonthAccumulator accumulator = monthMap.getOrDefault(monthKey, new FinanceMonthAccumulator());
            FinanceDashboardVO.MonthlyTrendItem item = new FinanceDashboardVO.MonthlyTrendItem();
            item.setMonth(monthKey);
            item.setIncome(accumulator.income);
            item.setExpense(accumulator.expense);
            item.setProfit(accumulator.income.subtract(accumulator.expense));
            trend.add(item);
            currentMonth = currentMonth.plusMonths(1);
        }
        return trend;
    }

    private FinanceDashboardVO.IncomeConcentration buildFinanceIncomeConcentration(Map<String, BigDecimal> incomeMap,
                                                                                   BigDecimal totalIncome) {
        List<BigDecimal> sortedAmounts = incomeMap.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getValue)
                .toList();

        BigDecimal top1 = sumTop(sortedAmounts, 1);
        BigDecimal top3 = sumTop(sortedAmounts, 3);
        BigDecimal top5 = sumTop(sortedAmounts, 5);

        FinanceDashboardVO.IncomeConcentration concentration = new FinanceDashboardVO.IncomeConcentration();
        concentration.setTop1Share(safeDivide(top1, totalIncome));
        concentration.setTop3Share(safeDivide(top3, totalIncome));
        concentration.setTop5Share(safeDivide(top5, totalIncome));
        concentration.setOtherShare(safeDivide(totalIncome.subtract(top5).max(ZERO), totalIncome));
        concentration.setSourceCount(incomeMap.size());
        return concentration;
    }

    private FinanceDashboardVO.PeriodComparison buildFinancePeriodComparison(String normalizedRange,
                                                                             FinanceAggregation currentAggregation) {
        if ("all".equals(normalizedRange)) {
            return null;
        }

        DateRange previousRange = resolvePreviousFinanceRange(normalizedRange);
        FinanceAggregation previousAggregation = aggregateFinanceRecords(queryFinanceRecords(previousRange));
        BigDecimal currentProfit = currentAggregation.totalIncome().subtract(currentAggregation.totalExpense());
        BigDecimal previousProfit = previousAggregation.totalIncome().subtract(previousAggregation.totalExpense());

        FinanceDashboardVO.PeriodComparison comparison = new FinanceDashboardVO.PeriodComparison();
        comparison.setIncomeChange(currentAggregation.totalIncome().subtract(previousAggregation.totalIncome()));
        comparison.setExpenseChange(currentAggregation.totalExpense().subtract(previousAggregation.totalExpense()));
        comparison.setProfitChange(currentProfit.subtract(previousProfit));
        comparison.setBaselineLabel(resolveFinanceBaselineLabel(normalizedRange));
        return comparison;
    }

    private TaxDashboardVO.PeriodComparison buildTaxPeriodComparison(String normalizedRange,
                                                                     Long companyId,
                                                                     TaxComparisonSnapshot currentSnapshot,
                                                                     Integer selectedYear) {
        if ("all".equals(normalizedRange)) {
            return null;
        }
        if (!StringUtils.hasText(normalizedRange) && selectedYear == null) {
            return null;
        }

        DateRange previousFinanceRange = resolvePreviousTaxFinanceRange(normalizedRange, selectedYear);
        DateRange previousTaxRange = resolvePreviousTaxPeriodRange(normalizedRange, selectedYear);
        TaxComparisonSnapshot previousSnapshot = buildTaxComparisonSnapshot(previousTaxRange, previousFinanceRange, companyId);

        TaxDashboardVO.PeriodComparison comparison = new TaxDashboardVO.PeriodComparison();
        comparison.setBaselineLabel(resolveTaxBaselineLabel(normalizedRange, selectedYear));
        comparison.setPreviousTaxBurdenRate(previousSnapshot.taxBurdenRate());
        comparison.setBurdenRateDelta(currentSnapshot.taxBurdenRate().subtract(previousSnapshot.taxBurdenRate()));
        comparison.setPreviousUnpaidTaxAmount(previousSnapshot.unpaidTaxAmount());
        comparison.setUnpaidTaxAmountDelta(currentSnapshot.unpaidTaxAmount().subtract(previousSnapshot.unpaidTaxAmount()));
        comparison.setPreviousPositiveTaxAmount(previousSnapshot.positiveTaxAmount());
        comparison.setPositiveTaxAmountDelta(currentSnapshot.positiveTaxAmount().subtract(previousSnapshot.positiveTaxAmount()));
        return comparison;
    }

    private TaxComparisonSnapshot buildTaxComparisonSnapshot(DateRange taxRange,
                                                             DateRange financeRange,
                                                             Long companyId) {
        BigDecimal positiveTaxAmount = ZERO;
        BigDecimal unpaidTaxAmount = ZERO;

        for (TaxRecord record : queryTaxRecords(companyId)) {
            if (!isTaxRecordWithinRange(record, taxRange)) {
                continue;
            }
            BigDecimal amount = safeAmount(record.getTaxAmount());
            if (amount.compareTo(ZERO) <= 0) {
                continue;
            }
            positiveTaxAmount = positiveTaxAmount.add(amount);
            if (record.getPaymentStatus() != null && record.getPaymentStatus() == 0) {
                unpaidTaxAmount = unpaidTaxAmount.add(amount);
            }
        }

        BigDecimal incomeBase = buildTaxIncomeBase(financeRange, companyId);
        return new TaxComparisonSnapshot(
                positiveTaxAmount,
                unpaidTaxAmount,
                safeDivide(positiveTaxAmount, incomeBase)
        );
    }

    private BigDecimal buildTaxIncomeBase(DateRange financeRange, Long companyId) {
        return queryFinanceRecords(financeRange, companyId).stream()
                .filter(record -> "income".equals(record.getType()))
                .map(FinanceRecord::getAmount)
                .filter(amount -> amount != null && amount.compareTo(ZERO) > 0)
                .reduce(ZERO, BigDecimal::add);
    }

    private List<TaxDashboardVO.OutstandingItem> buildRecentOutstanding(List<TaxRecord> records) {
        return records.stream()
                .filter(record -> record != null && record.getPaymentStatus() != null && record.getPaymentStatus() == 0)
                .filter(record -> safeAmount(record.getTaxAmount()).compareTo(ZERO) > 0)
                .sorted(
                        Comparator.comparing(
                                        (TaxRecord record) -> resolveTaxPeriodSortDate(record.getTaxPeriod())
                                )
                                .reversed()
                                .thenComparing(record -> safeAmount(record.getTaxAmount()), Comparator.reverseOrder())
                                .thenComparing(TaxRecord::getId, Comparator.reverseOrder())
                )
                .limit(5)
                .map(this::toOutstandingItem)
                .toList();
    }

    private List<HomeDashboardVO.DepartmentHeadcountItem> buildHomeDepartmentHeadcount(List<Employee> activeEmployees) {
        if (activeEmployees == null || activeEmployees.isEmpty()) {
            return List.of();
        }

        Map<String, Long> departmentMap = new LinkedHashMap<>();
        for (Employee employee : activeEmployees) {
            departmentMap.merge(normalizeDepartment(employee.getDepartment()), 1L, Long::sum);
        }

        return departmentMap.entrySet().stream()
                .sorted(
                        Map.Entry.<String, Long>comparingByValue().reversed()
                                .thenComparing(entry -> isUnassignedDepartment(entry.getKey()))
                                .thenComparing(Map.Entry::getKey)
                )
                .map(entry -> toDepartmentHeadcountItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<HomeDashboardVO.TaxCalendarItem> buildTaxCalendar() {
        QueryWrapper<TaxRecord> wrapper = new QueryWrapper<>();
        wrapper.select("id", "tax_period", "tax_type", "payment_status", "tax_amount");

        List<TaxRecord> records = taxRecordMapper.selectList(wrapper);
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        return records.stream()
                .sorted(
                        Comparator.comparing(
                                        (TaxRecord record) -> resolveTaxPeriodSortDate(record.getTaxPeriod())
                                )
                                .reversed()
                                .thenComparing(TaxRecord::getId, Comparator.reverseOrder())
                )
                .limit(8)
                .map(this::toTaxCalendarItem)
                .toList();
    }

    private List<String> generateHomeAiSummaryLines(Long companyId, Company company, HomeDashboardVO dashboard) {
        List<String> fallbackLines = buildHeuristicHomeAiSummaryLines(dashboard);
        if (!isAiSummaryEnabled()) {
            return fallbackLines;
        }

        OpenAiChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            return fallbackLines;
        }

        try {
            Response<AiMessage> response = chatModel.generate(List.of(
                    SystemMessage.from(buildHomeAiSummarySystemPrompt()),
                    UserMessage.from(buildHomeAiSummaryFacts(company, dashboard))
            ));
            String text = response == null || response.content() == null ? "" : response.content().text();
            List<String> aiLines = parseHomeAiSummaryLines(text);
            return aiLines.isEmpty() ? fallbackLines : aiLines;
        } catch (Exception exception) {
            log.warn("Failed to generate home AI summary for companyId={}", companyId, exception);
            return fallbackLines;
        }
    }

    private boolean isAiSummaryEnabled() {
        return aiProperties.isEnabled()
                && StringUtils.hasText(aiProperties.getApiKey())
                && StringUtils.hasText(aiProperties.getBaseUrl())
                && StringUtils.hasText(aiProperties.getModel());
    }

    private String buildHomeAiSummarySystemPrompt() {
        return """
                你是企业管理系统首页的 AI 经营速记助手。
                请根据提供的事实，输出 2 到 3 句简体中文短句。
                每句单独一行，每句不超过 28 个汉字。
                不要使用序号、项目符号、Markdown 或额外说明。
                只能引用已提供的数据，不得编造。
                优先概括经营走势、风险提醒和下一步可追问方向。
                """;
    }

    private String buildHomeAiSummaryFacts(Company company, HomeDashboardVO dashboard) {
        String companyName = company == null || !StringUtils.hasText(company.getName()) ? "当前企业" : company.getName().trim();
        String industry = company == null || !StringUtils.hasText(company.getIndustry()) ? "未填写" : company.getIndustry().trim();
        String taxpayerType = company == null || !StringUtils.hasText(company.getTaxpayerType()) ? "未填写" : company.getTaxpayerType().trim();
        String description = company == null || !StringUtils.hasText(company.getDescription()) ? "暂无企业画像" : company.getDescription().trim();

        String taxFocus = dashboard.getTaxCalendar() == null || dashboard.getTaxCalendar().isEmpty()
                ? "暂无税务节点"
                : dashboard.getTaxCalendar().stream()
                .limit(3)
                .map(item -> "%s %s %s %s".formatted(
                        item.getTaxPeriod(),
                        normalizeTaxType(item.getTaxType()),
                        getTaxStatusSummary(item.getStatus()),
                        formatCompactMoney(item.getAmount())
                ))
                .reduce((left, right) -> left + "；" + right)
                .orElse("暂无税务节点");

        StringBuilder builder = new StringBuilder();
        builder.append("公司名称：").append(companyName).append('\n');
        builder.append("行业：").append(industry).append('\n');
        builder.append("纳税人类型：").append(taxpayerType).append('\n');
        builder.append("企业画像：").append(description).append('\n');
        builder.append("本月收入：").append(formatCompactMoney(dashboard.getTotalIncome())).append('\n');
        builder.append("本月支出：").append(formatCompactMoney(dashboard.getTotalExpense())).append('\n');
        builder.append("本月净利润：").append(formatCompactMoney(dashboard.getNetProfit())).append('\n');
        builder.append("待缴税额：").append(formatCompactMoney(dashboard.getUnpaidTax())).append('\n');
        builder.append("近").append(HOME_TREND_MONTH_COUNT).append("个月趋势：")
                .append(buildTrendFacts(dashboard.getMonthlyTrend()))
                .append('\n');
        builder.append("税务关注：").append(taxFocus).append('\n');
        builder.append("请输出适合首页展示的 2 到 3 行经营速记。");
        return builder.toString();
    }

    private String buildTrendFacts(List<HomeDashboardVO.MonthlyTrendPoint> monthlyTrend) {
        if (monthlyTrend == null || monthlyTrend.isEmpty()) {
            return "暂无趋势数据";
        }
        return monthlyTrend.stream()
                .map(point -> "%s 收入%s 支出%s 利润%s".formatted(
                        point.getMonth(),
                        formatCompactMoney(point.getIncome()),
                        formatCompactMoney(point.getExpense()),
                        formatCompactMoney(point.getProfit())
                ))
                .reduce((left, right) -> left + "；" + right)
                .orElse("暂无趋势数据");
    }

    private List<String> parseHomeAiSummaryLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        for (String rawLine : text.split("\\R+")) {
            String normalized = rawLine.replaceFirst("^[-•*\\d.\\s]+", "").trim();
            if (!normalized.isEmpty()) {
                lines.add(normalized);
            }
            if (lines.size() == 3) {
                break;
            }
        }
        return lines;
    }

    private List<String> buildHeuristicHomeAiSummaryLines(HomeDashboardVO dashboard) {
        if (!hasHomeObservationData(dashboard)) {
            return List.of(
                    "近 %d 个月还没有形成可读经营画像。".formatted(HOME_TREND_MONTH_COUNT),
                    "先补充财务流水和税务档案，首页会给出更具体观察。"
            );
        }

        List<String> lines = new ArrayList<>();
        List<HomeDashboardVO.MonthlyTrendPoint> monthlyTrend = dashboard.getMonthlyTrend() == null
                ? List.of()
                : dashboard.getMonthlyTrend();
        HomeDashboardVO.MonthlyTrendPoint latest = monthlyTrend.isEmpty() ? null : monthlyTrend.getLast();
        HomeDashboardVO.MonthlyTrendPoint previous = monthlyTrend.size() > 1 ? monthlyTrend.get(monthlyTrend.size() - 2) : null;

        if (latest != null) {
            BigDecimal latestProfit = safeAmount(latest.getProfit());
            lines.add(latestProfit.compareTo(ZERO) >= 0
                    ? buildPositiveProfitLine(latest, previous)
                    : buildNegativeProfitLine(latest, previous));
        }

        long profitableMonths = monthlyTrend.stream()
                .map(HomeDashboardVO.MonthlyTrendPoint::getProfit)
                .map(this::safeAmount)
                .filter(amount -> amount.compareTo(ZERO) > 0)
                .count();
        HomeDashboardVO.MonthlyTrendPoint peakIncomePoint = monthlyTrend.stream()
                .filter(Objects::nonNull)
                .max(Comparator.comparing(point -> safeAmount(point.getIncome())))
                .orElse(null);
        if (peakIncomePoint != null) {
            lines.add("过去 %d 个月有 %d 个月盈利，收入高点在 %s。".formatted(
                    HOME_TREND_MONTH_COUNT,
                    profitableMonths,
                    formatMonthLabel(peakIncomePoint.getMonth())
            ));
        }

        if (safeAmount(dashboard.getUnpaidTax()).compareTo(ZERO) > 0) {
            lines.add("当前仍有 %s 待缴税额，建议优先处理最近税期。".formatted(
                    formatCompactMoney(dashboard.getUnpaidTax())
            ));
        } else {
            lines.add("当前没有待缴情形，税务节奏整体平稳。");
        }

        return lines.stream().limit(3).toList();
    }

    private boolean hasHomeObservationData(HomeDashboardVO dashboard) {
        if (dashboard == null) {
            return false;
        }
        if (safeAmount(dashboard.getTotalIncome()).compareTo(ZERO) > 0
                || safeAmount(dashboard.getTotalExpense()).compareTo(ZERO) > 0
                || safeAmount(dashboard.getUnpaidTax()).compareTo(ZERO) > 0) {
            return true;
        }
        if (dashboard.getTaxCalendar() != null && !dashboard.getTaxCalendar().isEmpty()) {
            return true;
        }
        return dashboard.getMonthlyTrend() != null && dashboard.getMonthlyTrend().stream().anyMatch(point ->
                safeAmount(point.getIncome()).compareTo(ZERO) != 0
                        || safeAmount(point.getExpense()).compareTo(ZERO) != 0
                        || safeAmount(point.getProfit()).compareTo(ZERO) != 0
        );
    }

    private String buildPositiveProfitLine(HomeDashboardVO.MonthlyTrendPoint latest,
                                           HomeDashboardVO.MonthlyTrendPoint previous) {
        String monthLabel = formatMonthLabel(latest.getMonth());
        if (previous == null) {
            return "%s 净利润 %s，经营状态已形成首个观察点。".formatted(
                    monthLabel,
                    formatCompactMoney(latest.getProfit())
            );
        }

        BigDecimal delta = safeAmount(latest.getProfit()).subtract(safeAmount(previous.getProfit()));
        String direction = delta.compareTo(ZERO) >= 0 ? "较上月回升" : "较上月收窄";
        return "%s 净利润 %s，%s。".formatted(
                monthLabel,
                formatCompactMoney(latest.getProfit()),
                direction
        );
    }

    private String buildNegativeProfitLine(HomeDashboardVO.MonthlyTrendPoint latest,
                                           HomeDashboardVO.MonthlyTrendPoint previous) {
        String monthLabel = formatMonthLabel(latest.getMonth());
        BigDecimal latestLoss = safeAmount(latest.getProfit()).abs();
        if (previous == null) {
            return "%s 出现亏损 %s，建议先核对支出波动。".formatted(
                    monthLabel,
                    formatCompactMoney(latestLoss)
            );
        }

        BigDecimal previousLoss = safeAmount(previous.getProfit()).abs();
        String direction = latestLoss.compareTo(previousLoss) > 0 ? "亏损较上月扩大" : "亏损较上月收敛";
        return "%s 出现亏损 %s，%s。".formatted(
                monthLabel,
                formatCompactMoney(latestLoss),
                direction
        );
    }

    private String getTaxStatusSummary(Integer status) {
        return switch (status == null ? -1 : status) {
            case 1 -> "已缴";
            case 2 -> "免征";
            default -> "待缴";
        };
    }

    private String formatCompactMoney(BigDecimal amount) {
        BigDecimal normalized = safeAmount(amount);
        BigDecimal absolute = normalized.abs();
        if (absolute.compareTo(BigDecimal.valueOf(10_000)) >= 0) {
            BigDecimal value = normalized.divide(BigDecimal.valueOf(10_000), 1, RoundingMode.HALF_UP);
            return "¥" + value.stripTrailingZeros().toPlainString() + "万";
        }
        return "¥" + normalized.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatMonthLabel(String month) {
        if (!StringUtils.hasText(month) || !month.contains("-")) {
            return "最近完整月";
        }
        String[] segments = month.split("-");
        if (segments.length != 2) {
            return month;
        }
        return "%s年%s月".formatted(segments[0], segments[1]);
    }

    private List<HrDashboardVO.MonthlyTrendItem> buildHrMonthlyTrend(List<Employee> activeEmployees, MonthRange monthRange) {
        if (activeEmployees.isEmpty()) {
            return List.of();
        }

        List<HrDashboardVO.MonthlyTrendItem> trend = new ArrayList<>();
        YearMonth current = monthRange.startMonth();
        while (!current.isAfter(monthRange.endMonth())) {
            LocalDate monthEnd = current.atEndOfMonth();
            long employeeCount = 0L;
            BigDecimal salaryAmount = ZERO;

            for (Employee employee : activeEmployees) {
                if (employee.getHireDate() != null && !employee.getHireDate().isAfter(monthEnd)) {
                    employeeCount++;
                    salaryAmount = salaryAmount.add(safeAmount(employee.getSalary()));
                }
            }

            HrDashboardVO.MonthlyTrendItem item = new HrDashboardVO.MonthlyTrendItem();
            item.setMonth(current.format(MONTH_FORMATTER));
            item.setEmployeeCount(employeeCount);
            item.setSalaryAmount(salaryAmount);
            trend.add(item);
            current = current.plusMonths(1);
        }
        return trend;
    }

    private HomeDashboardVO.TaxCalendarItem toTaxCalendarItem(TaxRecord record) {
        HomeDashboardVO.TaxCalendarItem item = new HomeDashboardVO.TaxCalendarItem();
        item.setTaxPeriod(record.getTaxPeriod());
        item.setTaxType(record.getTaxType());
        item.setStatus(record.getPaymentStatus());
        item.setAmount(record.getTaxAmount() == null ? BigDecimal.ZERO : record.getTaxAmount());
        return item;
    }

    private FinanceDashboardVO.ExpenseBreakdownItem toExpenseBreakdownItem(
            String name,
            BigDecimal amount,
            BigDecimal totalExpense
    ) {
        FinanceDashboardVO.ExpenseBreakdownItem item = new FinanceDashboardVO.ExpenseBreakdownItem();
        item.setName(name);
        item.setAmount(amount);
        item.setRatio(safeDivide(amount, totalExpense));
        return item;
    }

    private FinanceDashboardVO.TopIncomeSourceItem toTopIncomeSourceItem(String name, BigDecimal amount) {
        FinanceDashboardVO.TopIncomeSourceItem item = new FinanceDashboardVO.TopIncomeSourceItem();
        item.setName(name);
        item.setAmount(amount);
        return item;
    }

    private HrDashboardVO.DepartmentSalaryShareItem toDepartmentSalaryShareItem(
            String department,
            DepartmentAccumulator accumulator,
            BigDecimal totalSalary
    ) {
        HrDashboardVO.DepartmentSalaryShareItem item = new HrDashboardVO.DepartmentSalaryShareItem();
        item.setDepartment(department);
        item.setEmployeeCount(accumulator.employeeCount);
        item.setSalaryAmount(accumulator.salaryAmount);
        item.setRatio(safeDivide(accumulator.salaryAmount, totalSalary));
        return item;
    }

    private HomeDashboardVO.DepartmentHeadcountItem toDepartmentHeadcountItem(String department, Long employeeCount) {
        HomeDashboardVO.DepartmentHeadcountItem item = new HomeDashboardVO.DepartmentHeadcountItem();
        item.setDepartment(department);
        item.setEmployeeCount(employeeCount);
        return item;
    }

    private TaxDashboardVO.TaxTypeStructureItem toTaxTypeStructureItem(
            String taxType,
            BigDecimal amount,
            BigDecimal totalPositiveTax
    ) {
        TaxDashboardVO.TaxTypeStructureItem item = new TaxDashboardVO.TaxTypeStructureItem();
        item.setTaxType(taxType);
        item.setAmount(amount);
        item.setRatio(safeDivide(amount, totalPositiveTax));
        return item;
    }

    private TaxDashboardVO.StatusSummaryItem toStatusSummaryItem(Integer status, StatusAccumulator accumulator) {
        TaxDashboardVO.StatusSummaryItem item = new TaxDashboardVO.StatusSummaryItem();
        item.setStatus(status);
        item.setCount(accumulator.count);
        item.setAmount(accumulator.amount);
        return item;
    }

    private TaxDashboardVO.OutstandingItem toOutstandingItem(TaxRecord record) {
        TaxDashboardVO.OutstandingItem item = new TaxDashboardVO.OutstandingItem();
        item.setTaxPeriod(record.getTaxPeriod());
        item.setTaxType(normalizeTaxType(record.getTaxType()));
        item.setAmount(safeAmount(record.getTaxAmount()));
        return item;
    }

    private DateRange resolvePreviousFinanceRange(String range) {
        YearMonth lastClosedMonth = YearMonth.from(LocalDate.now(clock)).minusMonths(1);
        return switch (range) {
            case "last3months" -> new DateRange(lastClosedMonth.minusMonths(5).atDay(1), lastClosedMonth.minusMonths(3).atEndOfMonth());
            case "last6months" -> new DateRange(lastClosedMonth.minusMonths(11).atDay(1), lastClosedMonth.minusMonths(6).atEndOfMonth());
            case "last12months" -> new DateRange(lastClosedMonth.minusMonths(23).atDay(1), lastClosedMonth.minusMonths(12).atEndOfMonth());
            default -> throw new BusinessException("不支持的统计范围");
        };
    }

    private String resolveFinanceBaselineLabel(String range) {
        return switch (range) {
            case "last3months" -> "较前3个月";
            case "last6months" -> "较前6个月";
            case "last12months" -> "较前12个月";
            default -> "";
        };
    }

    private List<Integer> resolveAvailableTaxYears(List<TaxRecord> records) {
        return records.stream()
                .map(TaxRecord::getTaxPeriod)
                .map(this::extractTaxPeriodYear)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    private Integer resolveSelectedTaxYear(String range, List<Integer> availableYears) {
        Integer requestedYear = parseTaxRangeYear(range);
        if (requestedYear != null) {
            if (availableYears.isEmpty() || availableYears.contains(requestedYear)) {
                return requestedYear;
            }
            return availableYears.get(0);
        }

        String normalized = normalizeRange(range);
        if (!StringUtils.hasText(normalized)) {
            return availableYears.isEmpty() ? null : availableYears.get(0);
        }
        if (TAX_LEGACY_RANGES.contains(normalized)) {
            return null;
        }
        throw new BusinessException("不支持的税务统计范围");
    }

    private Integer parseTaxRangeYear(String range) {
        Matcher yearMatcher = YEAR_RANGE_PATTERN.matcher(normalizeRange(range));
        if (!yearMatcher.matches()) {
            return null;
        }
        return Integer.parseInt(yearMatcher.group(1));
    }

    private Integer extractTaxPeriodYear(String taxPeriod) {
        LocalDate periodDate = resolveTaxPeriodSortDate(taxPeriod);
        return LocalDate.MIN.equals(periodDate) ? null : periodDate.getYear();
    }

    private DateRange resolvePreviousTaxFinanceRange(String range, Integer selectedYear) {
        if (selectedYear != null) {
            return new DateRange(
                    LocalDate.of(selectedYear - 1, 1, 1),
                    LocalDate.of(selectedYear - 1, 12, 31)
            );
        }
        YearMonth currentMonth = YearMonth.from(LocalDate.now(clock));
        return switch (range) {
            case "thisYear" -> new DateRange(
                    LocalDate.of(currentMonth.getYear() - 1, 1, 1),
                    YearMonth.of(currentMonth.getYear() - 1, currentMonth.getMonthValue()).atEndOfMonth()
            );
            case "last12months" -> new DateRange(
                    currentMonth.minusMonths(23).atDay(1),
                    currentMonth.minusMonths(12).atEndOfMonth()
            );
            default -> throw new BusinessException("不支持的税务统计范围");
        };
    }

    private DateRange resolvePreviousTaxPeriodRange(String range, Integer selectedYear) {
        return resolvePreviousTaxFinanceRange(range, selectedYear);
    }

    private String resolveTaxBaselineLabel(String range, Integer selectedYear) {
        if (selectedYear != null) {
            return "较" + (selectedYear - 1) + "年度";
        }
        return switch (range) {
            case "thisYear" -> "较去年同期";
            case "last12months" -> "较前12个月";
            default -> "";
        };
    }

    private LocalDate resolveTaxPeriodSortDate(String taxPeriod) {
        if (taxPeriod == null) {
            return LocalDate.MIN;
        }

        Matcher monthMatcher = MONTH_PERIOD_PATTERN.matcher(taxPeriod);
        if (monthMatcher.matches()) {
            return YearMonth.parse(taxPeriod, MONTH_FORMATTER).atDay(1);
        }

        Matcher quarterMatcher = QUARTER_PERIOD_PATTERN.matcher(taxPeriod);
        if (quarterMatcher.matches()) {
            int year = Integer.parseInt(quarterMatcher.group(1));
            int quarter = Integer.parseInt(quarterMatcher.group(2));
            int month = (quarter - 1) * 3 + 1;
            return LocalDate.of(year, month, 1);
        }

        Matcher annualMatcher = ANNUAL_PERIOD_PATTERN.matcher(taxPeriod);
        if (annualMatcher.matches()) {
            int year = Integer.parseInt(annualMatcher.group(1));
            return LocalDate.of(year, 1, 1);
        }

        return LocalDate.MIN;
    }

    private DateRange resolveFinanceRange(String range) {
        String normalized = normalizeRange(range);
        if (!FINANCE_AND_HR_RANGES.contains(normalized)) {
            throw new BusinessException("不支持的统计范围");
        }

        YearMonth lastClosedMonth = YearMonth.from(LocalDate.now(clock)).minusMonths(1);
        return switch (normalized) {
            case "last3months" -> new DateRange(lastClosedMonth.minusMonths(2).atDay(1), lastClosedMonth.atEndOfMonth());
            case "last6months" -> new DateRange(lastClosedMonth.minusMonths(5).atDay(1), lastClosedMonth.atEndOfMonth());
            case "last12months" -> new DateRange(lastClosedMonth.minusMonths(11).atDay(1), lastClosedMonth.atEndOfMonth());
            case "all" -> new DateRange(null, null);
            default -> throw new BusinessException("不支持的统计范围");
        };
    }

    private DateRange resolveFinanceOrHrRange(String range) {
        String normalized = normalizeRange(range);
        if (!FINANCE_AND_HR_RANGES.contains(normalized)) {
            throw new BusinessException("不支持的统计范围");
        }

        YearMonth currentMonth = YearMonth.from(LocalDate.now(clock));
        return switch (normalized) {
            case "last3months" -> new DateRange(currentMonth.minusMonths(2).atDay(1), currentMonth.atEndOfMonth());
            case "last6months" -> new DateRange(currentMonth.minusMonths(5).atDay(1), currentMonth.atEndOfMonth());
            case "last12months" -> new DateRange(currentMonth.minusMonths(11).atDay(1), currentMonth.atEndOfMonth());
            case "all" -> new DateRange(null, null);
            default -> throw new BusinessException("不支持的统计范围");
        };
    }

    private MonthRange resolveHrMonthRange(String range, List<Employee> activeEmployees) {
        String normalized = normalizeRange(range);
        if (!FINANCE_AND_HR_RANGES.contains(normalized)) {
            throw new BusinessException("不支持的人事统计范围");
        }

        YearMonth currentMonth = YearMonth.from(LocalDate.now(clock));
        if (!"all".equals(normalized)) {
            DateRange dateRange = resolveFinanceOrHrRange(normalized);
            return new MonthRange(YearMonth.from(dateRange.start()), currentMonth);
        }

        if (activeEmployees.isEmpty()) {
            return new MonthRange(currentMonth, currentMonth);
        }

        LocalDate earliestHireDate = activeEmployees.stream()
                .map(Employee::getHireDate)
                .filter(date -> date != null)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now(clock));
        return new MonthRange(YearMonth.from(earliestHireDate), currentMonth);
    }

    private DateRange resolveTaxFinanceRange(String range, Integer selectedYear) {
        if (selectedYear != null) {
            return new DateRange(
                    LocalDate.of(selectedYear, 1, 1),
                    LocalDate.of(selectedYear, 12, 31)
            );
        }

        String normalized = normalizeRange(range);
        if (!StringUtils.hasText(normalized)) {
            return new DateRange(null, null);
        }
        if (!TAX_LEGACY_RANGES.contains(normalized)) {
            throw new BusinessException("不支持的税务统计范围");
        }

        YearMonth currentMonth = YearMonth.from(LocalDate.now(clock));
        return switch (normalized) {
            case "thisYear" -> new DateRange(LocalDate.of(currentMonth.getYear(), 1, 1), currentMonth.atEndOfMonth());
            case "last12months" -> new DateRange(currentMonth.minusMonths(11).atDay(1), currentMonth.atEndOfMonth());
            case "all" -> new DateRange(null, null);
            default -> throw new BusinessException("不支持的税务统计范围");
        };
    }

    private DateRange resolveTaxPeriodRange(String range, Integer selectedYear) {
        return resolveTaxFinanceRange(range, selectedYear);
    }

    private boolean isTaxRecordWithinRange(TaxRecord record, DateRange dateRange) {
        if (record == null) {
            return false;
        }

        LocalDate periodDate = resolveTaxPeriodSortDate(record.getTaxPeriod());
        if (periodDate.equals(LocalDate.MIN)) {
            return dateRange.start() == null && dateRange.end() == null;
        }
        if (dateRange.start() != null && periodDate.isBefore(dateRange.start())) {
            return false;
        }
        if (dateRange.end() != null && periodDate.isAfter(dateRange.end())) {
            return false;
        }
        return true;
    }

    private Map<Integer, StatusAccumulator> initStatusMap() {
        Map<Integer, StatusAccumulator> statusMap = new LinkedHashMap<>();
        statusMap.put(0, new StatusAccumulator());
        statusMap.put(1, new StatusAccumulator());
        statusMap.put(2, new StatusAccumulator());
        return statusMap;
    }

    private String normalizeRange(String range) {
        return range == null ? "" : range.trim();
    }

    private String normalizeFinanceCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return "未分类支出";
        }
        return category.trim();
    }

    private String resolveIncomeSourceName(FinanceRecord record) {
        if (record.getProject() != null && !record.getProject().trim().isEmpty()) {
            return record.getProject().trim();
        }
        if (record.getCategory() != null && !record.getCategory().trim().isEmpty()) {
            return record.getCategory().trim();
        }
        return "未标注来源";
    }

    private String normalizeDepartment(String department) {
        if (department == null || department.trim().isEmpty()) {
            return "未分配部门";
        }
        return department.trim();
    }

    private boolean isUnassignedDepartment(String department) {
        return normalizeDepartment(null).equals(department);
    }

    private String normalizeTaxType(String taxType) {
        if (taxType == null || taxType.trim().isEmpty()) {
            return "未标注税种";
        }
        return taxType.trim();
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? ZERO : amount;
    }

    private BigDecimal safeDivide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        return numerator.divide(denominator, 4, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal sumTop(List<BigDecimal> amounts, int limit) {
        return amounts.stream()
                .limit(limit)
                .reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal getBigDecimal(Map<String, Object> row, String key) {
        Object value = getValueIgnoreCase(row, key);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    private String getString(Map<String, Object> row, String key) {
        Object value = getValueIgnoreCase(row, key);
        return value == null ? "" : value.toString();
    }

    private Object getValueIgnoreCase(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }

    private record MonthRange(YearMonth startMonth, YearMonth endMonth) {
    }

    private record FinanceAggregation(BigDecimal totalIncome,
                                      BigDecimal totalExpense,
                                      Map<String, BigDecimal> incomeMap,
                                      Map<String, BigDecimal> expenseMap) {
    }

    private static class FinanceMonthAccumulator {
        private BigDecimal income = ZERO;
        private BigDecimal expense = ZERO;
    }

    private static class DepartmentAccumulator {
        private long employeeCount;
        private BigDecimal salaryAmount = ZERO;
    }

    private static class StatusAccumulator {
        private long count;
        private BigDecimal amount = ZERO;
    }

    private record TaxComparisonSnapshot(BigDecimal positiveTaxAmount,
                                         BigDecimal unpaidTaxAmount,
                                         BigDecimal taxBurdenRate) {
    }
}
