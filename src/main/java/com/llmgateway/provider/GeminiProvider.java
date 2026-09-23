package com.llmgateway.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.llmgateway.config.GeminiProperties;
import com.llmgateway.exception.ProviderException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Talks to Google's Gemini "generateContent" REST API.
 * <p>
 * Uses Spring's {@link RestClient} (a synchronous, fluent HTTP client introduced in
 * Spring 6.1 / Boot 3.2) rather than WebClient: this app runs on Tomcat with virtual
 * threads, so a blocking client is the right fit — no reactive types needed to get
 * the concurrency benefit.
 */
@Component
public class GeminiProvider implements LlmProvider {

    private final RestClient restClient;
    private final GeminiProperties properties;

    public GeminiProvider(RestClient.Builder restClientBuilder, GeminiProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public LlmProviderResponse generate(String prompt, String modelOverride, Integer maxTokens) {
        String model = modelOverride != null ? modelOverride : properties.model();
        GeminiRequest requestBody = new GeminiRequest(
                List.of(new Content(List.of(new Part(prompt)))),
                maxTokens != null ? new GenerationConfig(maxTokens) : null
        );

        GeminiApiResponse apiResponse;
        try {
            apiResponse = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent?key={apiKey}", model, properties.apiKey())
                    .body(requestBody)
                    .retrieve()
                    .body(GeminiApiResponse.class);
        } catch (RestClientException e) {
            throw new ProviderException("Gemini request failed: " + e.getMessage(), e);
        }

        if (apiResponse == null || apiResponse.candidates() == null || apiResponse.candidates().isEmpty()) {
            throw new ProviderException("Gemini returned no candidates (prompt may have been blocked)");
        }

        String text = apiResponse.candidates().get(0).content().parts().get(0).text();
        int tokensUsed = apiResponse.usageMetadata() != null
                ? apiResponse.usageMetadata().totalTokenCount()
                : 0;

        return new LlmProviderResponse(text, tokensUsed);
    }

    // ---- Gemini wire format (request/response JSON shapes) ----
    // Kept private to this class: nothing outside GeminiProvider should depend on
    // Gemini's specific JSON structure. LlmProviderResponse above is the stable
    // internal contract the rest of the app talks to.

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record GeminiRequest(List<Content> contents, GenerationConfig generationConfig) {
    }

    private record GenerationConfig(Integer maxOutputTokens) {
    }

    private record Content(List<Part> parts) {
    }

    private record Part(String text) {
    }

    private record GeminiApiResponse(List<Candidate> candidates, UsageMetadata usageMetadata) {
    }

    private record Candidate(Content content) {
    }

    private record UsageMetadata(int totalTokenCount) {
    }
}
