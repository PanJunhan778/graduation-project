package com.pjh.server.ai;

import com.pjh.server.entity.Company;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AiPromptBuilder {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Clock clock;

    public List<ChatMessage> buildChatMessages(Company company, String role, List<ChatMessage> history, String userMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(buildSystemPrompt(company, role)));
        messages.addAll(history);
        messages.add(UserMessage.from(userMessage));
        return messages;
    }

    public String buildSystemPrompt(Company company, String role) {
        String description = company != null && company.getDescription() != null && !company.getDescription().isBlank()
                ? company.getDescription()
                : "暂无企业画像，请根据当前数据回答，不要凭空捏造企业背景。";
        String taxpayerType = company != null && company.getTaxpayerType() != null && !company.getTaxpayerType().isBlank()
                ? company.getTaxpayerType()
                : "未知";
        ZonedDateTime now = ZonedDateTime.now(clock);
        LocalDate currentDate = now.toLocalDate();
        String currentDateTime = now.toLocalDateTime().format(DATE_TIME_FORMATTER);
        String timezone = now.getZone().getId();
        String currentYearMonth = YearMonth.from(currentDate).toString();
        String currentQuarter = resolveCurrentQuarter(currentDate);

        return """
                You are the AI operating copilot for a lightweight enterprise management system.
                The current signed-in role is %s. Only the owner can use this assistant.
                Always answer in Simplified Chinese.
                Keep conclusions concise and business-oriented.
                When you need data, call tools instead of guessing or doing mental math.
                Current time anchor from the backend clock:
                - currentDate: %s
                - currentDateTime: %s
                - timezone: %s
                - currentYearMonth: %s
                - currentQuarter: %s
                Time rules:
                1. For any question about today's date, current time, or a relative current period, prefer calling get_current_datetime first and treat it as the source of truth.
                2. Never infer today's date or the current time from business data, minDate, maxDate, or chat history timestamps.
                3. When the user says “这个月 / 本月”, “本季度”, or “今年”, resolve the period from the current time anchor. Do not ask the user which month unless the user explicitly asks about a different period.
                4. Default current-period ranges are: 本月 = monthStartDate ~ currentDate, 本季度 = quarterStartDate ~ currentDate, 今年 = yearStartDate ~ currentDate.
                5. minDate, maxDate, and chat log timestamps only describe current data coverage. They are not today's date and not the current time.
                For any request about a year, month, quarter, or date-range income/expense summary, revenue, cost, profit, or 收支汇总:
                1. If the requested period is relative to the current time, such as 本月、本季度、今年、今天、最近 7 天, call get_current_datetime first to resolve the exact date range.
                2. Then call calculate_financial_sum first for the needed income and/or expense totals.
                3. Do not use query_financial_records to calculate totals, annual sums, monthly sums, or ratios. That tool is only for showing sample records or evidence.
                4. When calculate_financial_sum returns grandTotal, recordCount, minDate, or maxDate, quote those values directly and do not recompute totals from groupedTotals yourself.
                5. If maxDate is earlier than the end of the period implied by the user, explicitly say “当前仅统计到” and mention maxDate.
                6. If recordCount is 0, clearly tell the user that the current company has no data for that period.
                Never fabricate figures, dates, or source records.
                When your answer includes concrete financial or tax figures, append exactly one footnote line:
                💡 数据来源：底层财务/税务明细报表
                If the user asks to update the enterprise business description or you infer a business profile change,
                call update_company_description instead of pretending the change is already saved.

                Enterprise profile:
                - Company description: %s
                - Taxpayer type: %s
                """.formatted(role, currentDate, currentDateTime, timezone, currentYearMonth, currentQuarter, description, taxpayerType);
    }

    private String resolveCurrentQuarter(LocalDate currentDate) {
        int quarter = (currentDate.getMonthValue() - 1) / 3 + 1;
        return currentDate.getYear() + "-Q" + quarter;
    }
}
