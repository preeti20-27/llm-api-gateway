package com.llmgateway.controller;

import com.llmgateway.dto.ChatResponse;
import com.llmgateway.security.ApiKeyAuthenticationFilter;
import com.llmgateway.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test: only the web layer is loaded, ChatService is mocked. This means we
 * don't need Gemini credentials or network access to test the controller's
 * request/response mapping and validation.
 *
 * GlobalExceptionHandler is picked up automatically by @WebMvcTest (it scans
 * @RestControllerAdvice beans), so the validation-error test exercises the real
 * error-response shape too.
 *
 * addFilters = false skips the Spring Security filter chain (including the
 * X-API-Key check added in Phase 2): this test is only about ChatController's
 * request/response mapping. Authentication itself is covered separately by
 * ApiKeyAuthenticationIntegrationTest, against a real database.
 *
 * ApiKeyAuthenticationFilter would otherwise still be picked up as a bean by this
 * slice — it's a @Component implementing Filter, which @WebMvcTest auto-detects
 * regardless of addFilters — and pull in its own dependencies (ApiKeyRepository,
 * ApiKeyHasher). excludeFilters keeps it out of this context entirely, since this
 * test isn't about security at all.
 */
@WebMvcTest(
        controllers = ChatController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ApiKeyAuthenticationFilter.class)
)
@AutoConfigureMockMvc(addFilters = false)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @Test
    void chat_withValidPrompt_returnsProviderResponse() throws Exception {
        when(chatService.chat(any())).thenReturn(
                new ChatResponse("Hello there!", "gemini", false, 12, 250));

        mockMvc.perform(post("/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"Say hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Hello there!"))
                .andExpect(jsonPath("$.provider").value("gemini"))
                .andExpect(jsonPath("$.cached").value(false))
                .andExpect(jsonPath("$.tokensUsed").value(12));
    }

    @Test
    void chat_withBlankPrompt_returns400WithErrorBody() throws Exception {
        mockMvc.perform(post("/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/v1/chat"));
    }

    @Test
    void chat_withMissingPromptField_returns400() throws Exception {
        mockMvc.perform(post("/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
