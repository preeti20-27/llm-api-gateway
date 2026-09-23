package com.llmgateway.usage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmgateway.provider.LlmProvider;
import com.llmgateway.provider.LlmProviderResponse;
import com.llmgateway.security.AdminTokenAuthenticationFilter;
import com.llmgateway.security.ApiKeyAuthenticationFilter;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full stack (real Postgres + Redis via Testcontainers), but with LlmProvider itself
 * mocked rather than ChatService — unlike the other integration tests, this one is
 * specifically about ChatService's own caching and usage-logging behavior, so
 * ChatService needs to be real. Only the actual Gemini HTTP call is faked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "admin.token=test-admin-token")
class UsageAndCacheIntegrationTest {

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
    private LlmProvider llmProvider;

    private String createApiKey() throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/keys")
                        .header(AdminTokenAuthenticationFilter.ADMIN_TOKEN_HEADER, "test-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"usage-cache-test\", \"tier\": \"FREE\"}"))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("apiKey").asText();
    }

    @Test
    void secondIdenticalRequest_isServedFromCache_andUsageAccumulatesForBoth() throws Exception {
        when(llmProvider.name()).thenReturn("gemini");
        when(llmProvider.generate(any(), any(), any())).thenReturn(new LlmProviderResponse("mock answer", 7));

        String apiKey = createApiKey();
        String body = "{\"prompt\": \"What is Java?\"}";

        mockMvc.perform(post("/v1/chat")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(false))
                .andExpect(jsonPath("$.response").value("mock answer"));

        mockMvc.perform(post("/v1/chat")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(true))
                .andExpect(jsonPath("$.response").value("mock answer"));

        // Proves the second call was actually served from cache, not re-generated.
        verify(llmProvider, times(1)).generate(any(), any(), any());

        // Usage logging is async — give the virtual-thread write a moment to land
        // before reading it back. A single bounded sleep, not a tight poll loop:
        // GET /v1/usage shares the same rate-limit bucket as /v1/chat (Phase 3),
        // so repeated polling here would itself eat into that budget.
        Thread.sleep(500);

        mockMvc.perform(get("/v1/usage")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(2))
                .andExpect(jsonPath("$.cacheHits").value(1))
                .andExpect(jsonPath("$.totalTokens").value(14)) // 7 + 7: the cache hit still reports the cached token count
                .andExpect(result -> {
                    double estimatedCostUsd = objectMapper.readTree(result.getResponse().getContentAsString())
                            .get("estimatedCostUsd").asDouble();
                    assertThat(estimatedCostUsd).isGreaterThan(0.0);
                });
    }
}
