package com.llmgateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmgateway.dto.ChatResponse;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-context test against a real Postgres (via Testcontainers): boots the whole
 * app, runs the real Flyway migration, and exercises the real security filter chain
 * end to end — create a key through the public API, then use that exact raw key to
 * authenticate a protected request. Nothing here is mocked except ChatService, so we
 * don't need a Gemini API key to run it.
 *
 * Creating a key is itself behind the admin token (see AdminTokenAuthenticationIntegrationTest
 * for that check in isolation), so a fixed test token is configured here purely as setup.
 *
 * A successful /v1/chat call now also passes through RateLimitFilter, which talks to
 * Redis — so this class needs a real Redis alongside Postgres. There's no @ServiceConnection
 * detector for a plain Redis GenericContainer (unlike Postgres), so its host/port are
 * wired in by hand via @DynamicPropertySource instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "admin.token=test-admin-token")
class ApiKeyAuthenticationIntegrationTest {

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

    @Test
    void createdKey_authenticatesSuccessfullyOnProtectedEndpoint() throws Exception {
        when(chatService.chat(any())).thenReturn(new ChatResponse("hi there", "gemini", false, 3, 42));

        MvcResult createResult = mockMvc.perform(post("/admin/keys")
                        .header(AdminTokenAuthenticationFilter.ADMIN_TOKEN_HEADER, "test-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"integration-test\", \"tier\": \"FREE\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String rawKey = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("apiKey").asText();

        mockMvc.perform(post("/v1/chat")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("hi there"));
    }

    @Test
    void chat_withoutApiKeyHeader_returns401WithConsistentErrorBody() throws Exception {
        mockMvc.perform(post("/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"hi\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/v1/chat"));
    }

    @Test
    void chat_withUnknownApiKey_returns401() throws Exception {
        mockMvc.perform(post("/v1/chat")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, "sk-this-key-does-not-exist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"hi\"}"))
                .andExpect(status().isUnauthorized());
    }
}
