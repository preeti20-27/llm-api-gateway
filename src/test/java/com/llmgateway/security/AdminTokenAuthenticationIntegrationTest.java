package com.llmgateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the /admin/** filter chain end to end against a real Postgres (the
 * migration must run for POST /admin/keys to succeed at all). A fixed token is
 * configured via @TestPropertySource so these tests don't depend on any real
 * ADMIN_TOKEN being set in the environment they run in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "admin.token=test-admin-token")
class AdminTokenAuthenticationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createKey_withCorrectAdminToken_returns201() throws Exception {
        mockMvc.perform(post("/admin/keys")
                        .header(AdminTokenAuthenticationFilter.ADMIN_TOKEN_HEADER, "test-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"authorized-caller\", \"tier\": \"FREE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiKey").exists());
    }

    @Test
    void createKey_withoutAdminTokenHeader_returns401WithConsistentErrorBody() throws Exception {
        mockMvc.perform(post("/admin/keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"no-token\", \"tier\": \"FREE\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/admin/keys"));
    }

    @Test
    void createKey_withWrongAdminToken_returns401() throws Exception {
        mockMvc.perform(post("/admin/keys")
                        .header(AdminTokenAuthenticationFilter.ADMIN_TOKEN_HEADER, "definitely-not-the-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"wrong-token\", \"tier\": \"FREE\"}"))
                .andExpect(status().isUnauthorized());
    }
}
