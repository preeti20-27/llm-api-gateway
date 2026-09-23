package com.llmgateway.controller;

import com.llmgateway.dto.ChatResponse;
import com.llmgateway.entity.ApiKey;
import com.llmgateway.entity.ApiKeyTier;
import com.llmgateway.security.ApiKeyAuthenticationToken;
import com.llmgateway.service.ChatService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
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
 * addFilters = false skips the Spring Security filter chain entirely: this test is
 * only about ChatController's request/response mapping. Authentication itself is
 * covered separately by ApiKeyAuthenticationIntegrationTest, against a real database.
 *
 * The controller now reads @AuthenticationPrincipal ApiKey directly (Phase 4, for
 * usage logging), so the method body needs *some* Authentication in the
 * SecurityContext. SecurityMockMvcRequestPostProcessors.authentication(...) will NOT
 * work here — its mechanism depends on the real security filter chain
 * (SecurityContextHolderFilter) to load the context it stashes, and addFilters=false
 * disables that chain too. Setting SecurityContextHolder directly on this thread
 * works regardless: MockMvc runs the whole request synchronously on the test thread,
 * so whatever's on the ThreadLocal when .perform() is called is what the controller
 * sees.
 */
@WebMvcTest(ChatController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @BeforeEach
    void setAuthentication() {
        ApiKey fakeApiKey = new ApiKey("irrelevant-hash", "test-caller", ApiKeyTier.FREE);
        SecurityContextHolder.getContext().setAuthentication(new ApiKeyAuthenticationToken(fakeApiKey));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void chat_withValidPrompt_returnsProviderResponse() throws Exception {
        when(chatService.chat(any(), any())).thenReturn(
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
