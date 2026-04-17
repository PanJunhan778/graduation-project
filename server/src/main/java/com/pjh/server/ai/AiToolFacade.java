package com.pjh.server.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pjh.server.common.AiConstants;
import com.pjh.server.entity.AuditLog;
import com.pjh.server.entity.Employee;
import com.pjh.server.entity.FinanceRecord;
import com.pjh.server.entity.TaxRecord;
import com.pjh.server.exception.BusinessException;
import com.pjh.server.mapper.AuditLogMapper;
import com.pjh.server.mapper.EmployeeMapper;
import com.pjh.server.mapper.FinanceRecordMapper;
import com.pjh.server.mapper.TaxRecordMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiToolFacade {

    private static final Pattern MONTH_PERIOD_PATTERN = Pattern.compile("^(\\d{4})-(0[1-9]|1[0-2])$");
    private static final Pattern QUARTER_PERIOD_PATTERN = Pattern.compile("^(\\d{4})-Q([1-4])$");
    private static final Pattern ANNUAL_PERIOD_PATTERN = Pattern.compile("^(\\d{4})-Annual$");

    private final FinanceRecordMapper financeRecordMapper;
    private final EmployeeMapper employeeMapper;
    private final TaxRecordMapper taxRecordMapper;
    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public List<ToolSpecification> toolSpecifications() {
        return List.of(
                tool("query_financial_records", "Query raw finance records for the current company. Return at most 50 items. Use this for evidence or sample records, not authoritative period totals.",
                        Map.of(
                                "startDate", stringProperty("Start date in yyyy-MM-dd"),
                                "endDate", stringProperty("End date in yyyy-MM-dd"),
                                "type", enumProperty("Finance type", List.of("income", "expense")),
                                "category", stringProperty("Finance category")
                        )),
                tool("query_employee_list", "Query employee records for the current company.",
                        Map.of(
                                "department", stringProperty("Department name"),
                                "status", integerProperty("1 for active, 0 for inactive")
                        )),
                tool("query_tax_records", "Query tax records for the current company. Return at most 50 items.",
                        Map.of(
                                "taxPeriod", stringProperty("Tax period, such as 2026-03 or 2026-Q1"),
                                "taxType", stringProperty("Tax type"),
                                "status", integerProperty("0 unpaid, 1 paid, 2 exempt")
                        )),
                tool("query_audit_logs", "Query audit logs for the current company. Return at most 50 items.",
                        Map.of(
                                "module", enumProperty("Business module", List.of("finance", "employee", "tax")),
                                "startDate", stringProperty("Start date in yyyy-MM-dd"),
                                "endDate", stringProperty("End date in yyyy-MM-dd")
                        )),
                tool("calculate_financial_sum", "Calculate authoritative finance totals for the current company in Java. Return grandTotal, recordCount, minDate, maxDate, and groupedTotals. Always use this first for year/month/date-range income-expense summaries.",
                        Map.of(
                                "startDate", stringProperty("Start date in yyyy-MM-dd"),
                                "endDate", stringProperty("End date in yyyy-MM-dd"),
                                "type", enumProperty("Finance type", List.of("income", "expense")),
                                "groupBy", enumProperty("Grouping dimension", List.of("category", "project", "date", "type"))
                        )),
                tool("calculate_tax_sum", "Calculate tax sums in Java for a tax period range.",
                        Map.of(
                                "startPeriod", stringProperty("Start tax period"),
                                "endPeriod", stringProperty("End tax period"),
                                "status", integerProperty("0 unpaid, 1 paid, 2 exempt")
                        ),
                        List.of("startPeriod", "endPeriod")),
                tool("get_business_snapshot", "Build a business snapshot for the target month.",
                        Map.of(
                                "yearMonth", stringProperty("Target month in yyyy-MM")
                        ),
                        List.of("yearMonth")),
                tool(AiConstants.ACTION_TYPE_UPDATE_COMPANY_DESCRIPTION, "Request a human-in-the-loop update of the enterprise description.",
                        Map.of(
                                "newDescription", stringProperty("The new company description to be reviewed")
                        ),
                        List.of("newDescription"))
        );
    }

    public AiToolExecutionOutcome execute(Long companyId, ToolExecutionRequest request) {
        JsonNode arguments = parseArguments(request.arguments());
        return switch (request.name()) {
            case "query_financial_records" -> AiToolExecutionOutcome.result(queryFinancialRecords(companyId, arguments));
            case "query_employee_list" -> AiToolExecutionOutcome.result(queryEmployeeList(companyId, arguments));
            case "query_tax_records" -> AiToolExecutionOutcome.result(queryTaxRecords(companyId, arguments));
            case "query_audit_logs" -> AiToolExecutionOutcome.result(queryAuditLogs(companyId, arguments));
            case "calculate_financial_sum" -> AiToolExecutionOutcome.result(calculateFinancialSum(companyId, arguments));
            case "calculate_tax_sum" -> AiToolExecutionOutcome.result(calculateTaxSum(companyId, arguments));
            case "get_business_snapshot" -> AiToolExecutionOutcome.result(getBusinessSnapshot(companyId, arguments));
            case AiConstants.ACTION_TYPE_UPDATE_COMPANY_DESCRIPTION -> AiToolExecutionOutcome.pendingUpdate(
                    requiredText(arguments, "newDescription")
            );
            default -> throw new BusinessException("不支持的 AI 工具: " + request.name());
        };
    }

    private String queryFinancialRecords(Long companyId, JsonNode arguments) {
        LambdaQueryWrapper<FinanceRecord> wrapper = new LambdaQueryWrapper<FinanceRecord>()
                .eq(FinanceRecord::getCompanyId, companyId)
                .orderByDesc(FinanceRecord::getDate)
                .orderByDesc(FinanceRecord::getId)
                .last("LIMIT 50");

        optionalText(arguments, "type").ifPresent(type -> wrapper.eq(FinanceRecord::getType, type));
        optionalText(arguments, "category").ifPresent(category -> wrapper.eq(FinanceRecord::getCategory, category));
        optionalDate(arguments, "startDate").ifPresent(date -> wrapper.ge(FinanceRecord::getDate, date));
        optionalDate(arguments, "endDate").ifPresent(date -> wrapper.le(FinanceRecord::getDate, date));

        List<Map<String, Object>> records = financeRecordMapper.selectList(wrapper).stream()
                .map(record -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", record.getId());
                    item.put("type", record.getType());
                    item.put("amount", record.getAmount());
                    item.put("category", record.getCategory());
                    item.put("project", record.getProject());
                    item.put("date", record.getDate());
                    item.put("remark", record.getRemark());
                    return item;
                })
                .toList();

        return writeJson(Map.of("records", records, "count", records.size()));
    }

    private String queryEmployeeList(Long companyId, JsonNode arguments) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<Employee>()
                .eq(Employee::getCompanyId, companyId)
                .orderByAsc(Employee::getDepartment)
                .orderByAsc(Employee::getId);

        optionalText(arguments, "department").ifPresent(department -> wrapper.eq(Employee::getDepartment, department));
        if (arguments.hasNonNull("status")) {
            wrapper.eq(Employee::getStatus, arguments.get("status").asInt());
        }

        List<Map<String, Object>> employees = employeeMapper.selectList(wrapper).stream()
                .map(employee -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", employee.getId());
                    item.put("name", employee.getName());
                    item.put("department", employee.getDepartment());
                    item.put("position", employee.getPosition());
                    item.put("salary", employee.getSalary());
                    item.put("hireDate", employee.getHireDate());
                    item.put("status", employee.getStatus());
                    item.put("remark", employee.getRemark());
                    return item;
                })
                .toList();

        return writeJson(Map.of("records", employees, "count", employees.size()));
    }

    private String queryTaxRecords(Long companyId, JsonNode arguments) {
        LambdaQueryWrapper<TaxRecord> wrapper = new LambdaQueryWrapper<TaxRecord>()
                .eq(TaxRecord::getCompanyId, companyId)
                .orderByDesc(TaxRecord::getUpdatedTime)
                .orderByDesc(TaxRecord::getId)
                .last("LIMIT 50");

        optionalText(arguments, "taxPeriod").ifPresent(taxPeriod -> wrapper.eq(TaxRecord::getTaxPeriod, taxPeriod));
        optionalText(arguments, "taxType").ifPresent(taxType -> wrapper.eq(TaxRecord::getTaxType, taxType));
        if (arguments.hasNonNull("status")) {
            wrapper.eq(TaxRecord::getPaymentStatus, arguments.get("status").asInt());
        }

        List<Map<String, Object>> records = taxRecordMapper.selectList(wrapper).stream()
                .map(record -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", record.getId());
                    item.put("taxPeriod", record.getTaxPeriod());
                    item.put("taxType", record.getTaxType());
                    item.put("declarationType", record.getDeclarationType());
                    item.put("taxAmount", record.getTaxAmount());
                    item.put("paymentStatus", record.getPaymentStatus());
                    item.put("paymentDate", record.getPaymentDate());
                    item.put("remark", record.getRemark());
                    return item;
                })
                .toList();

        return writeJson(Map.of("records", records, "count", records.size()));
    }

    private String queryAuditLogs(Long companyId, JsonNode arguments) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getCompanyId, companyId)
                .orderByDesc(AuditLog::getOperationTime)
                .orderByDesc(AuditLog::getId)
                .last("LIMIT 50");

        optionalText(arguments, "module").ifPresent(module -> wrapper.eq(AuditLog::getModule, module));
        optionalDate(arguments, "startDate").ifPresent(date -> wrapper.ge(AuditLog::getOperationTime, date.atStartOfDay()));
        optionalDate(arguments, "endDate").ifPresent(date -> wrapper.le(AuditLog::getOperationTime, date.atTime(23, 59, 59)));

        List<Map<String, Object>> logs = auditLogMapper.selectList(wrapper).stream()
                .map(log -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", log.getId());
                    item.put("module", log.getModule());
                    item.put("operationType", log.getOperationType());
                    item.put("targetId", log.getTargetId());
                    item.put("fieldName", log.getFieldName());
                    item.put("oldValue", log.getOldValue());
                    item.put("newValue", log.getNewValue());
                    item.put("operationTime", log.getOperationTime());
                    return item;
                })
                .toList();

        return writeJson(Map.of("records", logs, "count", logs.size()));
    }

    private String calculateFinancialSum(Long companyId, JsonNode arguments) {
        String groupBy = optionalText(arguments, "groupBy").orElse("category");
        validateFinancialGroupBy(groupBy);

        Optional<String> type = optionalText(arguments, "type");
        Optional<LocalDate> startDate = optionalDate(arguments, "startDate");
        Optional<LocalDate> endDate = optionalDate(arguments, "endDate");

        LambdaQueryWrapper<FinanceRecord> wrapper = new LambdaQueryWrapper<FinanceRecord>()
                .eq(FinanceRecord::getCompanyId, companyId)
                .orderByAsc(FinanceRecord::getDate)
                .orderByAsc(FinanceRecord::getId);

        type.ifPresent(value -> wrapper.eq(FinanceRecord::getType, value));
        startDate.ifPresent(value -> wrapper.ge(FinanceRecord::getDate, value));
        endDate.ifPresent(value -> wrapper.le(FinanceRecord::getDate, value));

        List<FinanceRecord> records = financeRecordMapper.selectList(wrapper);
        Map<String, BigDecimal> groupedTotals = new LinkedHashMap<>();
        BigDecimal grandTotal = BigDecimal.ZERO;
        LocalDate minDate = null;
        LocalDate maxDate = null;

        for (FinanceRecord record : records) {
            BigDecimal amount = record.getAmount() == null ? BigDecimal.ZERO : record.getAmount();
            grandTotal = grandTotal.add(amount);

            LocalDate recordDate = record.getDate();
            if (recordDate != null) {
                if (minDate == null || recordDate.isBefore(minDate)) {
                    minDate = recordDate;
                }
                if (maxDate == null || recordDate.isAfter(maxDate)) {
                    maxDate = recordDate;
                }
            }

            String groupKey = resolveFinancialGroupKey(record, groupBy);
            groupedTotals.merge(groupKey, amount, BigDecimal::add);
        }

        List<Map.Entry<String, BigDecimal>> sortedEntries = new ArrayList<>(groupedTotals.entrySet());
        sortedEntries.sort(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry::getKey));

        Map<String, BigDecimal> sortedGroupedTotals = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : sortedEntries) {
            sortedGroupedTotals.put(entry.getKey(), entry.getValue());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("groupBy", groupBy);
        type.ifPresent(value -> result.put("type", value));
        startDate.ifPresent(value -> result.put("requestedStartDate", value));
        endDate.ifPresent(value -> result.put("requestedEndDate", value));
        result.put("recordCount", records.size());
        result.put("minDate", minDate);
        result.put("maxDate", maxDate);
        result.put("grandTotal", grandTotal);
        result.put("groupedTotals", sortedGroupedTotals);
        return writeJson(result);
    }

    private String calculateTaxSum(Long companyId, JsonNode arguments) {
        String startPeriod = requiredText(arguments, "startPeriod");
        String endPeriod = requiredText(arguments, "endPeriod");
        int startKey = toTaxPeriodSortKey(startPeriod);
        int endKey = toTaxPeriodSortKey(endPeriod);

        LambdaQueryWrapper<TaxRecord> wrapper = new LambdaQueryWrapper<TaxRecord>()
                .eq(TaxRecord::getCompanyId, companyId);
        if (arguments.hasNonNull("status")) {
            wrapper.eq(TaxRecord::getPaymentStatus, arguments.get("status").asInt());
        }

        BigDecimal sum = taxRecordMapper.selectList(wrapper).stream()
                .filter(record -> {
                    int currentKey = toTaxPeriodSortKey(record.getTaxPeriod());
                    return currentKey >= startKey && currentKey <= endKey;
                })
                .map(TaxRecord::getTaxAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return writeJson(Map.of("total", sum));
    }

    private String getBusinessSnapshot(Long companyId, JsonNode arguments) {
        YearMonth yearMonth = parseYearMonth(requiredText(arguments, "yearMonth"));
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        LambdaQueryWrapper<FinanceRecord> financeWrapper = new LambdaQueryWrapper<FinanceRecord>()
                .eq(FinanceRecord::getCompanyId, companyId)
                .ge(FinanceRecord::getDate, startDate)
                .le(FinanceRecord::getDate, endDate);

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (FinanceRecord record : financeRecordMapper.selectList(financeWrapper)) {
            BigDecimal amount = record.getAmount() == null ? BigDecimal.ZERO : record.getAmount();
            if ("income".equals(record.getType())) {
                income = income.add(amount);
            } else if ("expense".equals(record.getType())) {
                expense = expense.add(amount);
            }
        }

        LambdaQueryWrapper<TaxRecord> taxWrapper = new LambdaQueryWrapper<TaxRecord>()
                .eq(TaxRecord::getCompanyId, companyId);

        BigDecimal paidTax = BigDecimal.ZERO;
        BigDecimal unpaidTax = BigDecimal.ZERO;
        List<Map<String, Object>> taxBreakdown = new LinkedList<>();
        for (TaxRecord record : taxRecordMapper.selectList(taxWrapper)) {
            if (!taxPeriodMatchesMonth(record.getTaxPeriod(), yearMonth)) {
                continue;
            }
            BigDecimal amount = record.getTaxAmount() == null ? BigDecimal.ZERO : record.getTaxAmount();
            if (record.getPaymentStatus() != null && record.getPaymentStatus() == 1) {
                paidTax = paidTax.add(amount);
            } else if (record.getPaymentStatus() != null && record.getPaymentStatus() == 0) {
                unpaidTax = unpaidTax.add(amount);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taxPeriod", record.getTaxPeriod());
            item.put("taxType", record.getTaxType());
            item.put("paymentStatus", record.getPaymentStatus());
            item.put("taxAmount", amount);
            taxBreakdown.add(item);
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("yearMonth", yearMonth.toString());
        snapshot.put("totalIncome", income);
        snapshot.put("totalExpense", expense);
        snapshot.put("netProfit", income.subtract(expense));
        snapshot.put("paidTax", paidTax);
        snapshot.put("unpaidTax", unpaidTax);
        snapshot.put("taxRecords", taxBreakdown);
        return writeJson(snapshot);
    }

    private ToolSpecification tool(String name, String description,
                                   Map<String, Map<String, Object>> properties) {
        return tool(name, description, properties, List.of());
    }

    private ToolSpecification tool(String name, String description,
                                   Map<String, Map<String, Object>> properties,
                                   List<String> required) {
        return ToolSpecification.builder()
                .name(name)
                .description(description)
                .parameters(ToolParameters.builder()
                        .type("object")
                        .properties(properties)
                        .required(new ArrayList<>(required))
                        .build())
                .build();
    }

    private Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }

    private Map<String, Object> integerProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "integer");
        property.put("description", description);
        return property;
    }

    private Map<String, Object> enumProperty(String description, List<?> values) {
        Map<String, Object> property = new LinkedHashMap<>();
        Object firstValue = values.isEmpty() ? null : values.get(0);
        property.put("type", firstValue instanceof Integer ? "integer" : "string");
        property.put("description", description);
        property.put("enum", values.toArray());
        return property;
    }

    private JsonNode parseArguments(String raw) {
        try {
            if (raw == null || raw.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new BusinessException("AI 工具参数解析失败");
        }
    }

    private Optional<String> optionalText(JsonNode arguments, String fieldName) {
        if (arguments == null || !arguments.hasNonNull(fieldName)) {
            return Optional.empty();
        }
        String value = arguments.get(fieldName).asText().trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    private String requiredText(JsonNode arguments, String fieldName) {
        return optionalText(arguments, fieldName)
                .orElseThrow(() -> new BusinessException("AI 工具缺少参数: " + fieldName));
    }

    private Optional<LocalDate> optionalDate(JsonNode arguments, String fieldName) {
        try {
            return optionalText(arguments, fieldName).map(LocalDate::parse);
        } catch (DateTimeParseException e) {
            throw new BusinessException("日期参数格式错误: " + fieldName);
        }
    }

    private YearMonth parseYearMonth(String text) {
        try {
            return YearMonth.parse(text);
        } catch (DateTimeParseException e) {
            throw new BusinessException("yearMonth 必须为 yyyy-MM");
        }
    }

    private boolean taxPeriodMatchesMonth(String taxPeriod, YearMonth targetMonth) {
        Matcher monthMatcher = MONTH_PERIOD_PATTERN.matcher(taxPeriod);
        if (monthMatcher.matches()) {
            return YearMonth.parse(taxPeriod).equals(targetMonth);
        }

        Matcher quarterMatcher = QUARTER_PERIOD_PATTERN.matcher(taxPeriod);
        if (quarterMatcher.matches()) {
            int year = Integer.parseInt(quarterMatcher.group(1));
            int quarter = Integer.parseInt(quarterMatcher.group(2));
            if (year != targetMonth.getYear()) {
                return false;
            }
            int startMonth = (quarter - 1) * 3 + 1;
            int endMonth = startMonth + 2;
            return targetMonth.getMonthValue() >= startMonth && targetMonth.getMonthValue() <= endMonth;
        }

        Matcher annualMatcher = ANNUAL_PERIOD_PATTERN.matcher(taxPeriod);
        return annualMatcher.matches() && Integer.parseInt(annualMatcher.group(1)) == targetMonth.getYear();
    }

    private int toTaxPeriodSortKey(String taxPeriod) {
        Matcher monthMatcher = MONTH_PERIOD_PATTERN.matcher(taxPeriod);
        if (monthMatcher.matches()) {
            return Integer.parseInt(monthMatcher.group(1)) * 100 + Integer.parseInt(monthMatcher.group(2));
        }

        Matcher quarterMatcher = QUARTER_PERIOD_PATTERN.matcher(taxPeriod);
        if (quarterMatcher.matches()) {
            int year = Integer.parseInt(quarterMatcher.group(1));
            int quarter = Integer.parseInt(quarterMatcher.group(2));
            return year * 100 + quarter * 3;
        }

        Matcher annualMatcher = ANNUAL_PERIOD_PATTERN.matcher(taxPeriod);
        if (annualMatcher.matches()) {
            return Integer.parseInt(annualMatcher.group(1)) * 100 + 12;
        }

        throw new BusinessException("税期格式不合法: " + taxPeriod);
    }

    private void validateFinancialGroupBy(String groupBy) {
        if (!List.of("category", "project", "date", "type").contains(groupBy)) {
            throw new BusinessException("calculate_financial_sum 的 groupBy 不合法");
        }
    }

    private String resolveFinancialGroupKey(FinanceRecord record, String groupBy) {
        return switch (groupBy) {
            case "category" -> normalizeGroupKey(record.getCategory());
            case "project" -> normalizeGroupKey(record.getProject());
            case "date" -> record.getDate() == null ? "未设置日期" : record.getDate().toString();
            case "type" -> normalizeGroupKey(record.getType());
            default -> throw new BusinessException("calculate_financial_sum 的 groupBy 不合法");
        };
    }

    private String normalizeGroupKey(String value) {
        return value == null || value.isBlank() ? "未分类" : value;
    }

    private BigDecimal readBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        return new BigDecimal(value.toString());
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BusinessException("AI 工具结果序列化失败");
        }
    }
}
