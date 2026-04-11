package com.pjh.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pjh.server.entity.Employee;
import com.pjh.server.entity.FinanceRecord;
import com.pjh.server.entity.TaxRecord;
import com.pjh.server.exception.BusinessException;
import com.pjh.server.mapper.EmployeeMapper;
import com.pjh.server.mapper.FinanceRecordMapper;
import com.pjh.server.mapper.TaxRecordMapper;
import com.pjh.server.service.DashboardService;
import com.pjh.server.vo.FinanceDashboardVO;
import com.pjh.server.vo.HomeDashboardVO;
import com.pjh.server.vo.HrDashboardVO;
import com.pjh.server.vo.TaxDashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final Set<String> FINANCE_AND_HR_RANGES = Set.of("last3months", "last6months", "last12months", "all");
    private static final Set<String> TAX_RANGES = Set.of("thisYear", "last12months", "all");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Pattern MONTH_PERIOD_PATTERN = Pattern.compile("^(\\d{4})-(0[1-9]|1[0-2])$");
    private static final Pattern QUARTER_PERIOD_PATTERN = Pattern.compile("^(\\d{4})-Q([1-4])$");
    private static final Pattern ANNUAL_PERIOD_PATTERN = Pattern.compile("^(\\d{4})-Annual$");

    private final FinanceRecordMapper financeRecordMapper;
    private final EmployeeMapper employeeMapper;
    private final TaxRecordMapper taxRecordMapper;
    private final Clock clock;

    @Override
    public HomeDashboardVO getHomeDashboard() {
        LocalDate today = LocalDate.now(clock);
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate monthStart = currentMonth.atDay(1);
        LocalDate monthEnd = currentMonth.atEndOfMonth();

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
        result.setMonthlyTrend(buildMonthlyTrend(currentMonth));
        result.setTaxCalendar(buildTaxCalendar());
        return result;
    }

    @Override
    public FinanceDashboardVO getFinanceDashboard(String range) {
        DateRange dateRange = resolveFinanceOrHrRange(range);
        List<FinanceRecord> records = queryFinanceRecords(dateRange);

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

        FinanceDashboardVO result = new FinanceDashboardVO();
        result.setTotalExpense(totalExpense);
        result.setTotalIncome(totalIncome);
        BigDecimal finalTotalExpense = totalExpense;
        result.setExpenseBreakdown(expenseMap.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .map(entry -> toExpenseBreakdownItem(entry.getKey(), entry.getValue(), finalTotalExpense))
                .toList());
        result.setTopIncomeSources(incomeMap.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(5)
                .map(entry -> toTopIncomeSourceItem(entry.getKey(), entry.getValue()))
                .toList());
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
        DateRange financeRange = resolveTaxFinanceRange(range);
        DateRange taxRange = resolveTaxPeriodRange(range);
        List<TaxRecord> records = queryTaxRecords().stream()
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

        BigDecimal incomeBase = queryFinanceRecords(financeRange).stream()
                .filter(record -> "income".equals(record.getType()))
                .map(FinanceRecord::getAmount)
                .filter(amount -> amount != null && amount.compareTo(ZERO) > 0)
                .reduce(ZERO, BigDecimal::add);

        TaxDashboardVO result = new TaxDashboardVO();
        result.setPositiveTaxAmount(positiveTaxAmount);
        result.setIncomeBase(incomeBase);
        result.setUnpaidTaxAmount(unpaidTaxAmount);
        result.setTaxBurdenRate(safeDivide(positiveTaxAmount, incomeBase));
        BigDecimal finalPositiveTaxAmount = positiveTaxAmount;
        result.setTaxTypeStructure(taxTypeMap.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .map(entry -> toTaxTypeStructureItem(entry.getKey(), entry.getValue(), finalPositiveTaxAmount))
                .toList());
        result.setStatusSummary(statusMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> toStatusSummaryItem(entry.getKey(), entry.getValue()))
                .toList());
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

    private List<Employee> queryActiveEmployees() {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<Employee>()
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

    private List<HomeDashboardVO.MonthlyTrendPoint> buildMonthlyTrend(YearMonth currentMonth) {
        YearMonth startMonth = currentMonth.minusMonths(5);
        LocalDate startDate = startMonth.atDay(1);
        LocalDate endDate = currentMonth.atEndOfMonth();

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
        for (int i = 0; i < 6; i++) {
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

    private DateRange resolveTaxFinanceRange(String range) {
        String normalized = normalizeRange(range);
        if (!TAX_RANGES.contains(normalized)) {
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

    private DateRange resolveTaxPeriodRange(String range) {
        return resolveTaxFinanceRange(range);
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

    private static class DepartmentAccumulator {
        private long employeeCount;
        private BigDecimal salaryAmount = ZERO;
    }

    private static class StatusAccumulator {
        private long count;
        private BigDecimal amount = ZERO;
    }
}
