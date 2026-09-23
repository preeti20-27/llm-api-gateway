package com.llmgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the "gemini.*" keys from application.yml (which in turn read from
 * environment variables — see .env.example). Kept as a record: it's immutable
 * and needs no boilerplate getters.
 */
@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(
        String apiKey,
        String baseUrl,
        String model
) {
}
