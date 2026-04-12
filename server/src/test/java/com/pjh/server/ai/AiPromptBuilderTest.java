package com.pjh.server.ai;

import com.pjh.server.entity.Company;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiPromptBuilderTest {

    private final AiPromptBuilder aiPromptBuilder = new AiPromptBuilder();

    @Test
    void buildSystemPromptShouldContainCompanyProfileAndTaxpayerType() {
        Company company = new Company();
        company.setDescription("主营国内电商与跨境订阅服务");
        company.setTaxpayerType("一般纳税人");

        String prompt = aiPromptBuilder.buildSystemPrompt(company, "owner");

        assertThat(prompt).contains("主营国内电商与跨境订阅服务");
        assertThat(prompt).contains("一般纳税人");
        assertThat(prompt).contains("Only the owner can use this assistant");
        assertThat(prompt).contains("💡 数据来源：底层财务/税务明细报表");
    }

    @Test
    void buildChatMessagesShouldKeepSystemHistoryAndCurrentInputOrder() {
        Company company = new Company();
        company.setDescription("主营教育咨询");
        company.setTaxpayerType("小规模纳税人");

        List<ChatMessage> messages = aiPromptBuilder.buildChatMessages(
                company,
                "owner",
                List.of(UserMessage.from("上一轮问题")),
                "本轮问题"
        );

        assertThat(messages).hasSize(3);
        assertThat(messages.getFirst()).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(1).text()).isEqualTo("上一轮问题");
        assertThat(messages.get(2).text()).isEqualTo("本轮问题");
    }
}
