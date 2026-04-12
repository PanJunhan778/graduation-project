package com.pjh.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private boolean enabled;

    private String baseUrl;

    private String apiKey;

    private String model = "qwen3.5-flash";

    private Duration timeout = Duration.ofSeconds(60);
}
