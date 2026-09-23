package com.llmgateway.controller;

import com.llmgateway.dto.CreateApiKeyResponse;
import com.llmgateway.entity.ApiKeyTier;
import com.llmgateway.security.ApiKeyAuthenticationFilter;
import com.llmgateway.service.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for request/response mapping only — ApiKeyService is mocked, so this
 * never touches a real database. Security filters are disabled here for the same
 * reason as in ChatControllerTest; the real end-to-end "create a key, then use it"
 * flow is covered by ApiKeyAuthenticationIntegrationTest.
 *
 * ApiKeyAuthenticationFilter would otherwise still be picked up as a bean by this
 * slice — it's a @Component implementing Filter, which @WebMvcTest auto-detects
 * regardless of addFilters — and pull in its own dependencies (ApiKeyRepository,
 * ApiKeyHasher). excludeFilters keeps it out of this context entirely, since this
 * test isn't about security at all.
 */
@WebMvcTest(
        controllers = AdminKeyController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ApiKeyAuthenticationFilter.class)
)
@AutoConfigureMockMvc(addFilters = false)
class AdminKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApiKeyService apiKeyService;

    @Test
    void createKey_returnsRawKeyOnceWith201() throws Exception {
        when(apiKeyService.createKey(any())).thenReturn(new CreateApiKeyResponse(
                UUID.randomUUID(), "test-app", ApiKeyTier.FREE, "sk-raw-key-value", Instant.now()));

        mockMvc.perform(post("/admin/keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"test-app\", \"tier\": \"FREE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiKey").value("sk-raw-key-value"))
                .andExpect(jsonPath("$.tier").value("FREE"));
    }

    @Test
    void createKey_withBlankName_returns400() throws Exception {
        mockMvc.perform(post("/admin/keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\", \"tier\": \"FREE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createKey_withMissingTier_returns400() throws Exception {
        mockMvc.perform(post("/admin/keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"test-app\"}"))
                .andExpect(status().isBadRequest());
    }
}
