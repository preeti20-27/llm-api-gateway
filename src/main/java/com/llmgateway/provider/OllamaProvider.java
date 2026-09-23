package com.llmgateway.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.llmgateway.config.OllamaProperties;
import com.llmgateway.exception.ProviderException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Talks to a locally running Ollama instance's REST API. This is the fallback
 * provider (Phase 5): no API key, no network egress beyond localhost, so it stays
 * available even if Gemini (or the internet connection to it) doesn't.
 */
@Component
public class OllamaProvider implements LlmProvider {

    private final RestClient restClient;
    private final OllamaProperties properties;

    public OllamaProvider(RestClient.Builder restClientBuilder, OllamaProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Override
    public String name() {
        return "ollama";
    }

    @Override
    public LlmProviderResponse generate(String prompt, String modelOverride, Integer maxTokens) {
        String model = modelOverride != null ? modelOverride : properties.model();
        // stream=false: a single JSON object back, matching how GeminiProvider works
        // and what ChatService expects — no reason to add streaming complexity here.
        OllamaRequest requestBody = new OllamaRequest(model, prompt, false);

        OllamaApiResponse apiResponse;
        try {
            apiResponse = restClient.post()
                    .uri("/api/generate")
                    .body(requestBody)
                    .retrieve()
                    .body(OllamaApiResponse.class);
        } catch (RestClientException e) {
            throw new ProviderException("Ollama request failed: " + e.getMessage(), e);
        }

        if (apiResponse == null || apiResponse.response() == null) {
            throw new ProviderException("Ollama returned no response");
        }

        // Ollama reports prompt and completion tokens separately; combined here to
        // match LlmProviderResponse's single tokensUsed field (same simplification
        // GeminiProvider makes with Gemini's totalTokenCount).
        int tokensUsed = zeroIfNull(apiResponse.promptEvalCount()) + zeroIfNull(apiResponse.evalCount());

        return new LlmProviderResponse(apiResponse.response(), tokensUsed, name());
    }

    private static int zeroIfNull(Integer value) {
        return value != null ? value : 0;
    }

    // ---- Ollama wire format ----

    private record OllamaRequest(String model, String prompt, boolean stream) {
    }

    private record OllamaApiResponse(
            String response,
            @JsonProperty("prompt_eval_count") Integer promptEvalCount,
            @JsonProperty("eval_count") Integer evalCount
    ) {
    }
}
