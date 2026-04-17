package com.pjh.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pjh.server.config.AiProperties;
import com.pjh.server.dashboard.HomeAiSummaryRefreshRequestedEvent;
import com.pjh.server.entity.Company;
import com.pjh.server.entity.HomeAiSummarySnapshot;
import com.pjh.server.entity.TaxRecord;
import com.pjh.server.mapper.CompanyMapper;
import com.pjh.server.mapper.FinanceRecordMapper;
import com.pjh.server.mapper.HomeAiSummarySnapshotMapper;
import com.pjh.server.mapper.TaxRecordMapper;
import com.pjh.server.vo.HomeAiSummaryVO;
import com.pjh.server.vo.HomeDashboardVO;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class HomeAiSummarySnapshotService {

    static final String STATUS_READY = "ready";
    static final String STATUS_REFRESHING = "refreshing";
    static final String STATUS_EMPTY = "empty";
    static final String STATUS_FAILED = "failed";

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int HOME_TREND_MONTH_COUNT = 6;
    private static final Duration REFRESH_TIMEOUT = Duration.ofSeconds(90);
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Pattern MONTH_PERIOD_PATTERN = Pattern.compile("^(\\d{4})-(0[1-9]|1[0-2])$");
    private static final Pattern QUARTER_PERIOD_PATTERN = Pattern.compile("^(\\d{4})-Q([1-4])$");
    private static final Pattern ANNUAL_PERIOD_PATTERN = Pattern.compile("^(\\d{4})-Annual$");
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final HomeAiSummarySnapshotMapper homeAiSummarySnapshotMapper;
    private final CompanyMapper companyMapper;
    private final FinanceRecordMapper financeRecordMapper;
    private final TaxRecordMapper taxRecordMapper;
    private final ObjectProvider<OpenAiChatModel> chatModelProvider;
    private final AiProperties aiProperties;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public HomeAiSummaryVO getHomeAiSummary(Long companyId) {
        HomeAiSummarySnapshot snapshot = homeAiSummarySnapshotMapper.selectByCompanyId(companyId);
        HomeDashboardVO dashboard = buildDashboard(companyId);

        if (!hasHomeObservationData(dashboard)) {
            HomeAiSummarySnapshot emptySnapshot = persistEmptySnapshot(companyId, snapshot);
            return toVo(emptySnapshot, List.of());
        }

        List<String> summaryLines = deserializeSummaryLines(snapshot == null ? null : snapshot.getSummaryLinesJson());
        boolean hasStoredSummary = !summaryLines.isEmpty();

        if (snapshot != null && STATUS_READY.equals(snapshot.getStatus()) && !isDirty(snapshot)) {
            return toVo(snapshot, summaryLines);
        }

        if (snapshot != null && STATUS_FAILED.equals(snapshot.getStatus()) && !hasStoredSummary) {
            return toVo(snapshot, summaryLines);
        }

        if (snapshot != null && STATUS_REFRESHING.equals(snapshot.getStatus()) && !isRefreshTimedOut(snapshot)) {
            return toVo(snapshot, summaryLines);
        }

        HomeAiSummarySnapshot refreshingSnapshot = persistRefreshingSnapshot(companyId, snapshot, summaryLines);
        eventPublisher.publishEvent(new HomeAiSummaryRefreshRequestedEvent(companyId));
        return toVo(refreshingSnapshot, summaryLines);
    }

    @Transactional
    public void markDirty(Long companyId) {
        HomeAiSummarySnapshot snapshot = homeAiSummarySnapshotMapper.selectByCompanyId(companyId);
        if (snapshot == null) {
            return;
        }

        List<String> summaryLines = deserializeSummaryLines(snapshot.getSummaryLinesJson());
        snapshot.setIsDirty(1);
        if (summaryLines.isEmpty() && STATUS_FAILED.equals(snapshot.getStatus())) {
            snapshot.setStatus(STATUS_EMPTY);
        }
        homeAiSummarySnapshotMapper.updateById(snapshot);
    }

    @Transactional
    public void refreshSummary(Long companyId) {
        HomeAiSummarySnapshot snapshot = homeAiSummarySnapshotMapper.selectByCompanyId(companyId);
        HomeDashboardVO dashboard = buildDashboard(companyId);

        if (!hasHomeObservationData(dashboard)) {
            persistEmptySnapshot(companyId, snapshot);
            return;
        }

        Company company = companyMapper.selectById(companyId);
        List<String> previousLines = deserializeSummaryLines(snapshot == null ? null : snapshot.getSummaryLinesJson());

        try {
            List<String> generatedLines = generateHomeAiSummaryLines(companyId, company, dashboard);
            HomeAiSummarySnapshot refreshedSnapshot = snapshot == null ? new HomeAiSummarySnapshot() : snapshot;
            if (refreshedSnapshot.getId() == null) {
                refreshedSnapshot.setCompanyId(companyId);
            }
            refreshedSnapshot.setSummaryLinesJson(serializeSummaryLines(generatedLines));
            refreshedSnapshot.setStatus(STATUS_READY);
            refreshedSnapshot.setIsDirty(0);
            refreshedSnapshot.setGeneratedAt(LocalDateTime.now(clock));
            refreshedSnapshot.setRefreshStartedAt(null);
            refreshedSnapshot.setLastError(null);
            saveSnapshot(refreshedSnapshot);
        } catch (Exception exception) {
            log.warn("Failed to refresh home AI summary, companyId={}", companyId, exception);
            HomeAiSummarySnapshot failedSnapshot = snapshot == null ? new HomeAiSummarySnapshot() : snapshot;
            if (failedSnapshot.getId() == null) {
                failedSnapshot.setCompanyId(companyId);
            }

            if (previousLines.isEmpty()) {
                failedSnapshot.setSummaryLinesJson(serializeSummaryLines(List.of()));
                failedSnapshot.setStatus(STATUS_FAILED);
                failedSnapshot.setGeneratedAt(null);
            } else {
                failedSnapshot.setSummaryLinesJson(serializeSummaryLines(previousLines));
                failedSnapshot.setStatus(STATUS_READY);
            }
            failedSnapshot.setIsDirty(1);
            failedSnapshot.setRefreshStartedAt(null);
            failedSnapshot.setLastError(truncateError(resolveErrorMessage(exception)));
            saveSnapshot(failedSnapshot);
        }
    }

    private HomeDashboardVO buildDashboard(Long companyId) {
        LocalDate today = LocalDate.now(clock);
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate monthStart = currentMonth.atDay(1);
        LocalDate monthEnd = currentMonth.atEndOfMonth();

        BigDecimal totalIncome = ZERO;
        BigDecimal totalExpense = ZERO;
        for (Map<String, Object> row : financeRecordMapper.selectCurrentMonthSummaryByCompanyId(companyId, monthStart, monthEnd)) {
            String type = getString(row, "type");
            BigDecimal total = getBigDecimal(row, "total");
            if ("income".equals(type)) {
                totalIncome = total;
            } else if ("expense".equals(type)) {
                totalExpense = total;
            }
        }

        BigDecimal unpaidTax = safeAmount(taxRecordMapper.selectUnpaidTaxTotalByCompanyId(companyId));

        HomeDashboardVO dashboard = new HomeDashboardVO();
        dashboard.setTotalIncome(totalIncome);
        dashboard.setTotalExpense(totalExpense);
        dashboard.setNetProfit(totalIncome.subtract(totalExpense));
        dashboard.setUnpaidTax(unpaidTax);
        dashboard.setHasUnpaidWarning(unpaidTax.compareTo(ZERO) > 0);
        dashboard.setMonthlyTrend(buildMonthlyTrend(companyId, currentMonth.minusMonths(1)));
        dashboard.setTaxCalendar(buildTaxCalendar(companyId));
        dashboard.setDepartmentHeadcount(List.of());
        dashboard.setSetupStatus(buildSetupStatus(companyId));
        return dashboard;
    }

    private HomeDashboardVO.SetupStatus buildSetupStatus(Long companyId) {
        HomeDashboardVO.SetupStatus setupStatus = new HomeDashboardVO.SetupStatus();
        setupStatus.setHasStaffAccount(false);
        setupStatus.setHasFinanceRecord(safeCount(financeRecordMapper.selectCountByCompanyId(companyId)) > 0);
        return setupStatus;
    }

    private List<HomeDashboardVO.MonthlyTrendPoint> buildMonthlyTrend(Long companyId, YearMonth endMonth) {
        YearMonth startMonth = endMonth.minusMonths(HOME_TREND_MONTH_COUNT - 1L);
        LocalDate startDate = startMonth.atDay(1);
        LocalDate endDate = endMonth.atEndOfMonth();

        Map<String, Map<String, Object>> rowMap = new LinkedHashMap<>();
        for (Map<String, Object> row : financeRecordMapper.selectHomeMonthlyTrendByCompanyId(companyId, startDate, endDate)) {
            rowMap.put(getString(row, "month"), row);
        }

        List<HomeDashboardVO.MonthlyTrendPoint> trend = new ArrayList<>();
        for (int i = 0; i < HOME_TREND_MONTH_COUNT; i++) {
            YearMonth month = startMonth.plusMonths(i);
            String monthKey = month.format(MONTH_FORMATTER);
            Map<String, Object> row = rowMap.get(monthKey);

            BigDecimal income = row == null ? ZERO : getBigDecimal(row, "income");
            BigDecimal expense = row == null ? ZERO : getBigDecimal(row, "expense");

            HomeDashboardVO.MonthlyTrendPoint point = new HomeDashboardVO.MonthlyTrendPoint();
            point.setMonth(monthKey);
            point.setIncome(income);
            point.setExpense(expense);
            point.setProfit(income.subtract(expense));
            trend.add(point);
        }
        return trend;
    }

    private List<HomeDashboardVO.TaxCalendarItem> buildTaxCalendar(Long companyId) {
        List<TaxRecord> records = taxRecordMapper.selectHomeTaxCalendarRecordsByCompanyId(companyId);
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        return records.stream()
                .sorted(
                        Comparator.comparing((TaxRecord record) -> resolveTaxPeriodSortDate(record.getTaxPeriod()))
                                .reversed()
                                .thenComparing(TaxRecord::getId, Comparator.reverseOrder())
                )
                .limit(8)
                .map(this::toTaxCalendarItem)
                .toList();
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

    private List<String> generateHomeAiSummaryLines(Long companyId, Company company, HomeDashboardVO dashboard) {
        if (!isAiSummaryEnabled()) {
            throw new IllegalStateException("AI 摘要未启用");
        }

        OpenAiChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new IllegalStateException("AI 模型未初始化");
        }

        Response<AiMessage> response = chatModel.generate(List.of(
                SystemMessage.from(buildHomeAiSummarySystemPrompt()),
                UserMessage.from(buildHomeAiSummaryFacts(company, dashboard))
        ));
        String text = response == null || response.content() == null ? "" : response.content().text();
        List<String> aiLines = parseHomeAiSummaryLines(text);
        if (aiLines.isEmpty()) {
            throw new IllegalStateException("AI 摘要返回为空");
        }
        return aiLines;
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

    private HomeAiSummarySnapshot persistEmptySnapshot(Long companyId, HomeAiSummarySnapshot snapshot) {
        HomeAiSummarySnapshot emptySnapshot = snapshot == null ? new HomeAiSummarySnapshot() : snapshot;
        if (emptySnapshot.getId() == null) {
            emptySnapshot.setCompanyId(companyId);
        }
        emptySnapshot.setSummaryLinesJson(serializeSummaryLines(List.of()));
        emptySnapshot.setStatus(STATUS_EMPTY);
        emptySnapshot.setIsDirty(0);
        emptySnapshot.setGeneratedAt(null);
        emptySnapshot.setRefreshStartedAt(null);
        emptySnapshot.setLastError(null);
        saveSnapshot(emptySnapshot);
        return emptySnapshot;
    }

    private HomeAiSummarySnapshot persistRefreshingSnapshot(Long companyId,
                                                            HomeAiSummarySnapshot snapshot,
                                                            List<String> summaryLines) {
        HomeAiSummarySnapshot refreshingSnapshot = snapshot == null ? new HomeAiSummarySnapshot() : snapshot;
        if (refreshingSnapshot.getId() == null) {
            refreshingSnapshot.setCompanyId(companyId);
            refreshingSnapshot.setGeneratedAt(null);
        }
        refreshingSnapshot.setSummaryLinesJson(serializeSummaryLines(summaryLines));
        refreshingSnapshot.setStatus(STATUS_REFRESHING);
        refreshingSnapshot.setIsDirty(1);
        refreshingSnapshot.setRefreshStartedAt(LocalDateTime.now(clock));
        refreshingSnapshot.setLastError(null);
        saveSnapshot(refreshingSnapshot);
        return refreshingSnapshot;
    }

    private void saveSnapshot(HomeAiSummarySnapshot snapshot) {
        if (snapshot.getId() == null) {
            homeAiSummarySnapshotMapper.insert(snapshot);
        } else {
            homeAiSummarySnapshotMapper.updateById(snapshot);
        }
    }

    private boolean isDirty(HomeAiSummarySnapshot snapshot) {
        return snapshot != null && Integer.valueOf(1).equals(snapshot.getIsDirty());
    }

    private boolean isRefreshTimedOut(HomeAiSummarySnapshot snapshot) {
        if (snapshot == null || snapshot.getRefreshStartedAt() == null) {
            return true;
        }
        Duration duration = Duration.between(snapshot.getRefreshStartedAt(), LocalDateTime.now(clock));
        return duration.compareTo(REFRESH_TIMEOUT) > 0;
    }

    private HomeAiSummaryVO toVo(HomeAiSummarySnapshot snapshot, List<String> summaryLines) {
        HomeAiSummaryVO vo = new HomeAiSummaryVO();
        vo.setSummaryLines(summaryLines);
        vo.setGeneratedAt(snapshot == null || snapshot.getGeneratedAt() == null ? null : snapshot.getGeneratedAt().toString());
        vo.setStatus(snapshot == null ? STATUS_EMPTY : snapshot.getStatus());
        return vo;
    }

    private List<String> deserializeSummaryLines(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<String> lines = objectMapper.readValue(json, STRING_LIST_TYPE);
            return lines == null ? List.of() : lines.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .toList();
        } catch (Exception exception) {
            log.warn("Failed to parse stored home AI summary json", exception);
            return List.of();
        }
    }

    private String serializeSummaryLines(List<String> summaryLines) {
        try {
            return objectMapper.writeValueAsString(summaryLines == null ? List.of() : summaryLines);
        } catch (Exception exception) {
            throw new IllegalStateException("首页 AI 摘要序列化失败", exception);
        }
    }

    private String truncateError(String message) {
        if (!StringUtils.hasText(message)) {
            return "首页 AI 摘要刷新失败";
        }
        String normalized = message.trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private String resolveErrorMessage(Exception exception) {
        if (exception == null) {
            return "首页 AI 摘要刷新失败";
        }
        if (StringUtils.hasText(exception.getMessage())) {
            return exception.getMessage();
        }
        return exception.getClass().getSimpleName();
    }

    private HomeDashboardVO.TaxCalendarItem toTaxCalendarItem(TaxRecord record) {
        HomeDashboardVO.TaxCalendarItem item = new HomeDashboardVO.TaxCalendarItem();
        item.setTaxPeriod(record.getTaxPeriod());
        item.setTaxType(record.getTaxType());
        item.setStatus(record.getPaymentStatus());
        item.setAmount(safeAmount(record.getTaxAmount()));
        return item;
    }

    private String normalizeTaxType(String taxType) {
        if (taxType == null || taxType.trim().isEmpty()) {
            return "未标注税种";
        }
        return taxType.trim();
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

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? ZERO : amount;
    }

    private long safeCount(Long count) {
        return count == null ? 0L : count;
    }

    private BigDecimal getBigDecimal(Map<String, Object> row, String key) {
        Object value = getValueIgnoreCase(row, key);
        if (value == null) {
            return ZERO;
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
}
