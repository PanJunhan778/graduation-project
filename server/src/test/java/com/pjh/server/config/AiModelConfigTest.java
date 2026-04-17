package com.pjh.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pjh.server.ai.AiToolFacade;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiModelConfigTest {

    private final AiModelConfig aiModelConfig = new AiModelConfig();

    @Test
    void resolveTokenizerShouldFallbackForCompatibleModelNamesUnknownToJtokkit() {
        AiProperties properties = buildProperties();
        properties.setModel("qwen3.5-flash");

        Tokenizer tokenizer = aiModelConfig.resolveTokenizer(properties);

        assertThat(tokenizer.estimateTokenCountInText("你好，帮我汇总今天的经营数据")).isPositive();
    }

    @Test
    void resolveTokenizerShouldRespectExplicitTokenizerModelOverride() {
        AiProperties properties = buildProperties();
        properties.setModel("qwen3.5-flash");
        properties.setTokenizerModel("gpt-3.5-turbo");

        Tokenizer tokenizer = aiModelConfig.resolveTokenizer(properties);

        assertThat(tokenizer.estimateTokenCountInText("hello")).isPositive();
    }

    @Test
    void resolveTokenizerShouldEstimateTokenCountForToolSpecifications() {
        AiProperties properties = buildProperties();
        properties.setModel("qwen3.5-flash");

        Tokenizer tokenizer = aiModelConfig.resolveTokenizer(properties);
        AiToolFacade toolFacade = new AiToolFacade(null, null, null, null, new ObjectMapper());

        assertThat(tokenizer.estimateTokenCountInToolSpecifications(toolFacade.toolSpecifications())).isPositive();
    }

    @Test
    void aiModelBeansShouldShareTokenizerConfiguration() {
        AiProperties properties = buildProperties();
        properties.setModel("qwen3.5-flash");

        OpenAiChatModel chatModel = aiModelConfig.aiChatModel(properties);
        OpenAiStreamingChatModel streamingChatModel = aiModelConfig.aiStreamingChatModel(properties);

        assertThat(chatModel).isNotNull();
        assertThat(streamingChatModel).isNotNull();
        assertThat(chatModel.modelName()).isEqualTo("qwen3.5-flash");
        assertThat(streamingChatModel.modelName()).isEqualTo("qwen3.5-flash");
    }

    private AiProperties buildProperties() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        properties.setApiKey("test-key");
        return properties;
    }
}
