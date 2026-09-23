package com.llmgateway.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmgateway.dto.ChatResponse;
import com.llmgateway.security.AdminTokenAuthenticationFilter;
import com.llmgateway.security.ApiKeyAuthenticationFilter;
import com.llmgateway.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies rate limiting is actually wired into the real /v1/chat request path:
 * headers, 429 status, and per-key isolation, against the real SecurityConfig filter
 * chain (RateLimiterTest already covers the token-bucket algorithm itself in
 * isolation, faster, without the rest of the app).
 *
 * rate-limit.free.capacity is overridden to 3 so tests don't need to fire 10+
 * requests to observe the limit kick in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "admin.token=test-admin-token",
        "rate-limit.free.capacity=3",
        "rate-limit.free.window-seconds=60"
})
class RateLimitIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChatService chatService;

    private String createApiKey(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/keys")
                        .header(AdminTokenAuthenticationFilter.ADMIN_TOKEN_HEADER, "test-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"tier\": \"FREE\"}"))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("apiKey").asText();
    }

    private void chatRequest(String apiKey, org.springframework.test.web.servlet.ResultMatcher... matchers) throws Exception {
        var result = mockMvc.perform(post("/v1/chat")
                .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\": \"hi\"}"));
        for (var matcher : matchers) {
            result.andExpect(matcher);
        }
    }

    @Test
    void requestsWithinCapacity_succeedAndDecrementRemaining() throws Exception {
        when(chatService.chat(any())).thenReturn(new ChatResponse("ok", "gemini", false, 1, 5));
        String apiKey = createApiKey("within-capacity");

        chatRequest(apiKey, status().isOk(), header().string("X-RateLimit-Remaining", "2"));
        chatRequest(apiKey, status().isOk(), header().string("X-RateLimit-Remaining", "1"));
    }

    @Test
    void exceedingCapacity_returns429WithRetryAfterAndZeroRemaining() throws Exception {
        when(chatService.chat(any())).thenReturn(new ChatResponse("ok", "gemini", false, 1, 5));
        String apiKey = createApiKey("exceeding-capacity");

        chatRequest(apiKey, status().isOk());
        chatRequest(apiKey, status().isOk());
        chatRequest(apiKey, status().isOk());

        chatRequest(apiKey,
                status().isTooManyRequests(),
                header().string("X-RateLimit-Remaining", "0"),
                header().exists("Retry-After"));
    }

    @Test
    void differentApiKeys_haveIndependentBuckets() throws Exception {
        when(chatService.chat(any())).thenReturn(new ChatResponse("ok", "gemini", false, 1, 5));
        String keyA = createApiKey("bucket-a");
        String keyB = createApiKey("bucket-b");

        chatRequest(keyA, status().isOk());
        chatRequest(keyA, status().isOk());
        chatRequest(keyA, status().isOk());
        // key A is now exhausted, but key B has its own independent bucket
        chatRequest(keyB, status().isOk(), header().string("X-RateLimit-Remaining", "2"));
    }
}
