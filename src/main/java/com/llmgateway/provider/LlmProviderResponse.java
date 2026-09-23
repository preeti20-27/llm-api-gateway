package com.llmgateway.provider;

/**
 * Result of a provider call, before the service layer wraps it into the public
 * ChatResponse. provider is self-reported by whichever LlmProvider actually
 * produced this result — necessary from Phase 5 onward, where FailoverLlmProvider
 * might answer via Gemini or fall back to Ollama on any one call, so ChatService
 * can't just ask "which provider is this" once and assume it applies to every call.
 */
public record LlmProviderResponse(String text, int tokensUsed, String provider) {
}
