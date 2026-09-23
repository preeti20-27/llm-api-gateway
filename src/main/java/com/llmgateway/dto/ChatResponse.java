package com.llmgateway.dto;

/**
 * Response body for POST /v1/chat.
 *
 * @param response   the generated text
 * @param provider   which provider answered, e.g. "gemini" or "ollama" (fallback added in Phase 5)
 * @param cached     true if this was served from cache (cache added in Phase 4; always false for now)
 * @param tokensUsed tokens consumed by this request, as reported by the provider
 * @param latencyMs  end-to-end time spent generating the response
 */
public record ChatResponse(
        String response,
        String provider,
        boolean cached,
        int tokensUsed,
        long latencyMs
) {
}
