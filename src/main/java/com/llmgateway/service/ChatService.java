package com.llmgateway.service;

import com.llmgateway.dto.ChatRequest;
import com.llmgateway.dto.ChatResponse;
import com.llmgateway.provider.LlmProvider;
import com.llmgateway.provider.LlmProviderResponse;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a chat request: calls the provider, times it, shapes the response.
 * <p>
 * For now there is exactly one {@link LlmProvider} bean (Gemini), so Spring injects
 * it directly. From Phase 5 onward, provider selection (primary + failover) will be
 * pulled out into its own component rather than growing this class.
 */
@Service
public class ChatService {

    private final LlmProvider llmProvider;

    public ChatService(LlmProvider llmProvider) {
        this.llmProvider = llmProvider;
    }

    public ChatResponse chat(ChatRequest request) {
        long start = System.currentTimeMillis();
        LlmProviderResponse result = llmProvider.generate(request.prompt(), request.model(), request.maxTokens());
        long latencyMs = System.currentTimeMillis() - start;

        return new ChatResponse(
                result.text(),
                llmProvider.name(),
                false, // caching arrives in Phase 4
                result.tokensUsed(),
                latencyMs
        );
    }
}
