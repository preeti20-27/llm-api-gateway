package com.llmgateway.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmgateway.provider.FailoverLlmProvider;
import com.llmgateway.provider.LlmProviderResponse;
import com.llmgateway.security.AdminTokenAuthenticationFilter;
import com.llmgateway.security.ApiKeyAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies actuator is reachable without any of the app's own auth (Prometheus and
 * a browser hitting /actuator/health don't have an API key or admin token), and that
 * a real chat request's metrics actually show up in the Prometheus scrape format —
 * not just that GatewayMetrics records to *some* registry (GatewayMetricsTest
 * already covers that in isolation), but that the whole export pipeline works.
 * <p>
 * @AutoConfigureObservability is required here: @SpringBootTest disables full
 * metrics-export auto-configuration by default (swapping in a bare SimpleMeterRegistry,
 * for test performance/isolation) and skips PrometheusMetricsExportAutoConfiguration
 * entirely — so /actuator/prometheus is simply never registered without this. Confirmed
 * by running the real app: it exposes 4 actuator endpoints; without this annotation, a
 * @SpringBootTest for the exact same app exposes only 3 ("Exposing 3 endpoint(s)..." in
 * the log) — prometheus missing.
 */
@SpringBootTest
@AutoConfigureObservability
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "admin.token=test-admin-token")
class ActuatorIntegrationTest {

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
    private FailoverLlmProvider llmProvider;

    private String createApiKey() throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/keys")
                        .header(AdminTokenAuthenticationFilter.ADMIN_TOKEN_HEADER, "test-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"actuator-test\", \"tier\": \"FREE\"}"))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("apiKey").asText();
    }

    @Test
    void health_isReachableWithoutAnyAuthHeader() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void prometheus_exposesCustomMetricsAfterAChatRequest() throws Exception {
        when(llmProvider.generate(any(), any(), any())).thenReturn(new LlmProviderResponse("hi", 2, "gemini"));

        String apiKey = createApiKey();
        mockMvc.perform(post("/v1/chat")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"hi\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("llm_gateway_chat_requests_total")))
                .andExpect(content().string(containsString("llm_gateway_chat_latency_seconds")));
    }
}
