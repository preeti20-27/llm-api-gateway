package com.llmgateway.provider;

import com.llmgateway.config.GeminiProperties;
import com.llmgateway.exception.ProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

/**
 * @RestClientTest boots just enough Spring context to auto-configure a
 * RestClient.Builder backed by a MockRestServiceServer — no real HTTP call, no
 * Gemini API key needed, and much faster than a full @SpringBootTest.
 */
@RestClientTest(GeminiProvider.class)
class GeminiProviderTest {

    @Autowired
    private GeminiProvider geminiProvider;

    @Autowired
    private MockRestServiceServer server;

    @TestConfiguration
    static class TestConfig {
        @Bean
        GeminiProperties geminiProperties() {
            return new GeminiProperties("test-api-key", "https://generativelanguage.googleapis.com", "gemini-1.5-flash");
        }
    }

    @Test
    void generate_parsesTextAndTokenCountFromGeminiResponse() {
        String geminiJson = """
                {
                  "candidates": [
                    { "content": { "parts": [ { "text": "Hello, world!" } ] } }
                  ],
                  "usageMetadata": { "totalTokenCount": 7 }
                }
                """;

        server.expect(requestToUriTemplate(
                        "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}",
                        "gemini-1.5-flash", "test-api-key"))
                .andExpect(method(POST))
                .andRespond(withSuccess(geminiJson, MediaType.APPLICATION_JSON));

        LlmProviderResponse result = geminiProvider.generate("Say hi", null, null);

        assertThat(result.text()).isEqualTo("Hello, world!");
        assertThat(result.tokensUsed()).isEqualTo(7);
        server.verify();
    }

    @Test
    void generate_whenGeminiCallFails_throwsProviderException() {
        server.expect(requestToUriTemplate(
                        "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}",
                        "gemini-1.5-flash", "test-api-key"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> geminiProvider.generate("Say hi", null, null))
                .isInstanceOf(ProviderException.class);
    }

    @Test
    void generate_whenNoCandidatesReturned_throwsProviderException() {
        String emptyJson = """
                { "candidates": [] }
                """;

        server.expect(requestToUriTemplate(
                        "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}",
                        "gemini-1.5-flash", "test-api-key"))
                .andRespond(withSuccess(emptyJson, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> geminiProvider.generate("Say hi", null, null))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("blocked");
    }
}
