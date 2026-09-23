package com.llmgateway.service;

import com.llmgateway.cache.CacheKeyGenerator;
import com.llmgateway.cache.CachedChatResult;
import com.llmgateway.cache.ResponseCache;
import com.llmgateway.dto.ChatRequest;
import com.llmgateway.dto.ChatResponse;
import com.llmgateway.provider.LlmProvider;
import com.llmgateway.provider.LlmProviderResponse;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Orchestrates a chat request: checks the cache, calls the provider on a miss,
 * times the whole thing, records usage, shapes the response.
 * <p>
 * For now there is exactly one {@link LlmProvider} bean (Gemini), so Spring injects
 * it directly. From Phase 5 onward, provider selection (primary + failover) will be
 * pulled out into its own component rather than growing this class.
 */
@Service
public class ChatService {

    private final LlmProvider llmProvider;
    private final ResponseCache responseCache;
    private final CacheKeyGenerator cacheKeyGenerator;
    private final UsageLogService usageLogService;

    public ChatService(LlmProvider llmProvider, ResponseCache responseCache,
                        CacheKeyGenerator cacheKeyGenerator, UsageLogService usageLogService) {
        this.llmProvider = llmProvider;
        this.responseCache = responseCache;
        this.cacheKeyGenerator = cacheKeyGenerator;
        this.usageLogService = usageLogService;
    }

    public ChatResponse chat(ChatRequest request, UUID apiKeyId) {
        long start = System.currentTimeMillis();
        String cacheKey = cacheKeyGenerator.generate(request.model(), request.prompt(), request.maxTokens());

        ChatResponse response = responseCache.get(cacheKey)
                .map(cached -> fromCache(cached, start))
                .orElseGet(() -> generateAndCache(request, cacheKey, start));

        // Fired after the response is already built — the client is never kept
        // waiting on this write. See the Phase 4 write-up for why that's safe here.
        usageLogService.recordAsync(apiKeyId, response.provider(), response.tokensUsed(),
                response.cached(), response.latencyMs());

        return response;
    }

    private ChatResponse fromCache(CachedChatResult cached, long start) {
        long latencyMs = System.currentTimeMillis() - start;
        return new ChatResponse(cached.text(), cached.provider(), true, cached.tokensUsed(), latencyMs);
    }

    private ChatResponse generateAndCache(ChatRequest request, String cacheKey, long start) {
        LlmProviderResponse result = llmProvider.generate(request.prompt(), request.model(), request.maxTokens());
        responseCache.put(cacheKey, new CachedChatResult(result.text(), result.tokensUsed(), llmProvider.name()));

        long latencyMs = System.currentTimeMillis() - start;
        return new ChatResponse(result.text(), llmProvider.name(), false, result.tokensUsed(), latencyMs);
    }
}
