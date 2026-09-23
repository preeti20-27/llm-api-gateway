package com.llmgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds "ollama.*" from application.yml. Ollama runs locally (no API key), so
 * unlike GeminiProperties there's no secret here — just where to find it.
 */
@ConfigurationProperties(prefix = "ollama")
public record OllamaProperties(String baseUrl, String model) {
}
