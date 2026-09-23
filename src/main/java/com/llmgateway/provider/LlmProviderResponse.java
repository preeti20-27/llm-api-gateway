package com.llmgateway.provider;

/**
 * Result of a provider call, before the service layer wraps it into the public ChatResponse.
 */
public record LlmProviderResponse(String text, int tokensUsed) {
}
