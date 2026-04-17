package com.pjh.server.config;

import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiModelConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
    public OpenAiChatModel aiChatModel(AiProperties properties) {
        validateEnabledConfiguration(properties);
        Tokenizer tokenizer = resolveTokenizer(properties);
        return OpenAiChatModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .modelName(properties.getModel())
                .timeout(properties.getTimeout())
                .maxRetries(1)
                .tokenizer(tokenizer)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
    public OpenAiStreamingChatModel aiStreamingChatModel(AiProperties properties) {
        validateEnabledConfiguration(properties);
        Tokenizer tokenizer = resolveTokenizer(properties);
        return OpenAiStreamingChatModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .modelName(properties.getModel())
                .timeout(properties.getTimeout())
                .tokenizer(tokenizer)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
    public ApplicationRunner aiStartupReporter(AiProperties properties) {
        validateEnabledConfiguration(properties);
        return args -> log.info(
                "AI service enabled: baseUrl={}, model={}, timeout={}",
                properties.getBaseUrl(),
                properties.getModel(),
                properties.getTimeout()
        );
    }

    private void validateEnabledConfiguration(AiProperties properties) {
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new IllegalStateException("AI 已启用，但 app.ai.base-url 不能为空");
        }
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("AI 已启用，但 app.ai.api-key 不能为空");
        }
        if (!StringUtils.hasText(properties.getModel())) {
            throw new IllegalStateException("AI 已启用，但 app.ai.model 不能为空");
        }
    }
    Tokenizer resolveTokenizer(AiProperties properties) {
        String tokenizerModel = StringUtils.hasText(properties.getTokenizerModel())
                ? properties.getTokenizerModel().trim()
                : properties.getModel();

        try {
            OpenAiTokenizer tokenizer = new OpenAiTokenizer(tokenizerModel);
            tokenizer.estimateTokenCountInText("tokenizer_probe");
            if (!tokenizerModel.equals(properties.getModel())) {
                log.info(
                        "AI tokenizer override enabled: model={}, tokenizerModel={}",
                        properties.getModel(),
                        tokenizerModel
                );
            }
            return tokenizer;
        } catch (RuntimeException e) {
            OpenAiTokenizer fallbackTokenizer = new OpenAiTokenizer(OpenAiChatModelName.GPT_3_5_TURBO);
            log.warn(
                    "AI model tokenizer fallback applied: model={}, configuredTokenizerModel={}, fallbackTokenizerModel={}, reason={}",
                    properties.getModel(),
                    properties.getTokenizerModel(),
                    OpenAiChatModelName.GPT_3_5_TURBO,
                    e.getMessage()
            );
            return fallbackTokenizer;
        }
    }
}
