package com.llmgateway.provider;

/**
 * A backend that can turn a prompt into text. Each implementation talks to one
 * concrete LLM (Gemini, Ollama, ...). The rest of the app depends only on this
 * interface, so swapping or adding providers never touches the controller/service layer.
 */
public interface LlmProvider {

    /**
     * Short identifier returned to clients in ChatResponse.provider, e.g. "gemini".
     */
    String name();

    /**
     * Generates a completion for the given prompt.
     *
     * @param prompt    the user's prompt, never blank
     * @param model     model override, or null to use this provider's configured default
     * @param maxTokens token cap, or null to use this provider's configured default
     * @throws com.llmgateway.exception.ProviderException if the upstream call fails
     */
    LlmProviderResponse generate(String prompt, String model, Integer maxTokens);
}
