package com.pjh.server.ai;

import com.pjh.server.entity.Company;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AiPromptBuilder {

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

        return """
                You are the AI operating copilot for a lightweight enterprise management system.
                The current signed-in role is %s. Only the owner can use this assistant.
                Always answer in Simplified Chinese.
                Keep conclusions concise and business-oriented.
                When you need data, call tools instead of guessing or doing mental math.
                For any request about a year, month, quarter, or date-range income/expense summary, revenue, cost, profit, or 收支汇总:
                1. You must call calculate_financial_sum first for the needed income and/or expense totals.
                2. Do not use query_financial_records to calculate totals, annual sums, monthly sums, or ratios. That tool is only for showing sample records or evidence.
                3. When calculate_financial_sum returns grandTotal, recordCount, minDate, or maxDate, quote those values directly and do not recompute totals from groupedTotals yourself.
                4. If maxDate is earlier than the end of the period implied by the user, explicitly say “当前仅统计到” and mention maxDate.
                5. If recordCount is 0, clearly tell the user that the current company has no data for that period.
                Never fabricate figures, dates, or source records.
                When your answer includes concrete financial or tax figures, append exactly one footnote line:
                💡 数据来源：底层财务/税务明细报表
                If the user asks to update the enterprise business description or you infer a business profile change,
                call update_company_description instead of pretending the change is already saved.

                Enterprise profile:
                - Company description: %s
                - Taxpayer type: %s
                """.formatted(role, description, taxpayerType);
    }
}
