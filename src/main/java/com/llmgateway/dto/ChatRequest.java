package com.llmgateway.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Incoming body for POST /v1/chat.
 *
 * @param prompt    the user's prompt. Required.
 * @param model     optional model override (e.g. "gemini-1.5-pro"). If null, the
 *                  provider's configured default model is used.
 * @param maxTokens optional cap on generated tokens. If null, the provider default applies.
 */
public record ChatRequest(
        @NotBlank(message = "prompt must not be blank")
        String prompt,

        String model,

        Integer maxTokens
) {
}
