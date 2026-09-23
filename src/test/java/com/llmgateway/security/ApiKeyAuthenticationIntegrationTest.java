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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ApiKeyAuthenticationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

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

    @Test
    void adminKeysEndpoint_doesNotRequireApiKey() throws Exception {
        // Documents current Phase 2 scope: /admin/keys is open. Locking it down is
        // tracked as a follow-up (see SecurityConfig), not silently assumed done.
        mockMvc.perform(post("/admin/keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"no-auth-needed\", \"tier\": \"FREE\"}"))
                .andExpect(status().isCreated());
    }
}
