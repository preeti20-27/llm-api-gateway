package com.llmgateway.service;

import com.llmgateway.cache.CacheKeyGenerator;
import com.llmgateway.cache.CachedChatResult;
import com.llmgateway.cache.ResponseCache;
import com.llmgateway.dto.ChatRequest;
import com.llmgateway.dto.ChatResponse;
import com.llmgateway.provider.LlmProvider;
import com.llmgateway.provider.LlmProviderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private LlmProvider llmProvider;

    @Mock
    private ResponseCache responseCache;

    @Mock
    private CacheKeyGenerator cacheKeyGenerator;

    @Mock
    private UsageLogService usageLogService;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(llmProvider, responseCache, cacheKeyGenerator, usageLogService);
    }

    @Test
    void chat_onCacheMiss_callsProviderAndCachesResult() {
        UUID apiKeyId = UUID.randomUUID();
        ChatRequest request = new ChatRequest("hello", null, null);

        when(cacheKeyGenerator.generate(null, "hello", null)).thenReturn("cache-key-abc");
        when(responseCache.get("cache-key-abc")).thenReturn(Optional.empty());
        when(llmProvider.generate("hello", null, null)).thenReturn(new LlmProviderResponse("hi there", 5, "gemini"));

        ChatResponse response = chatService.chat(request, apiKeyId);

        assertThat(response.response()).isEqualTo("hi there");
        assertThat(response.cached()).isFalse();
        assertThat(response.provider()).isEqualTo("gemini");
        assertThat(response.tokensUsed()).isEqualTo(5);

        verify(responseCache).put(eq("cache-key-abc"), eq(new CachedChatResult("hi there", 5, "gemini")));
        verify(usageLogService).recordAsync(eq(apiKeyId), eq("gemini"), eq(5), eq(false), anyLong());
    }

    @Test
    void chat_onCacheHit_skipsProviderAndReturnsCachedTrue() {
        UUID apiKeyId = UUID.randomUUID();
        ChatRequest request = new ChatRequest("hello", null, null);

        when(cacheKeyGenerator.generate(null, "hello", null)).thenReturn("cache-key-abc");
        when(responseCache.get("cache-key-abc"))
                .thenReturn(Optional.of(new CachedChatResult("hi there", 5, "gemini")));

        ChatResponse response = chatService.chat(request, apiKeyId);

        assertThat(response.cached()).isTrue();
        assertThat(response.response()).isEqualTo("hi there");
        assertThat(response.provider()).isEqualTo("gemini");
        assertThat(response.tokensUsed()).isEqualTo(5);

        verifyNoInteractions(llmProvider);
        verify(usageLogService).recordAsync(eq(apiKeyId), eq("gemini"), eq(5), eq(true), anyLong());
    }
}
