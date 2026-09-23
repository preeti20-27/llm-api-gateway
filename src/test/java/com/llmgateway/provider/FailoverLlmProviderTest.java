package com.llmgateway.provider;

import com.llmgateway.exception.ProviderException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @CircuitBreaker/@Retry/@TimeLimiter are Spring AOP method interceptors — they only
 * take effect through a Spring-managed proxy, so this has to be a real (if narrow)
 * Spring context rather than a plain `new FailoverLlmProvider(...)` unit test; a
 * plain instantiation would silently skip all three decorators and this test would
 * prove nothing.
 */
@SpringBootTest
@Testcontainers
class FailoverLlmProviderTest {

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

    @MockBean
    private GeminiProvider geminiProvider;

    @MockBean
    private OllamaProvider ollamaProvider;

    @Autowired
    private FailoverLlmProvider failoverLlmProvider;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetCircuitBreaker() {
        // Each test should see the "gemini" breaker start CLOSED, regardless of what
        // a previous test method did to it — Resilience4j's registry is a
        // context-scoped singleton, shared across test methods in this class.
        circuitBreakerRegistry.circuitBreaker("gemini").reset();
    }

    @Test
    void whenGeminiSucceeds_answersDirectlyFromGemini() {
        when(geminiProvider.generate("hello", null, null))
                .thenReturn(new LlmProviderResponse("gemini answer", 5, "gemini"));

        LlmProviderResponse response = failoverLlmProvider.generate("hello", null, null);

        assertThat(response.text()).isEqualTo("gemini answer");
        assertThat(response.provider()).isEqualTo("gemini");
        verifyNoInteractions(ollamaProvider);
    }

    @Test
    void whenGeminiFails_fallsBackToOllama() {
        when(geminiProvider.generate(any(), any(), any()))
                .thenThrow(new ProviderException("Gemini is down"));
        when(ollamaProvider.generate("hello", null, null))
                .thenReturn(new LlmProviderResponse("fallback answer", 3, "ollama"));

        LlmProviderResponse response = failoverLlmProvider.generate("hello", null, null);

        assertThat(response.text()).isEqualTo("fallback answer");
        assertThat(response.provider()).isEqualTo("ollama");
        verify(ollamaProvider).generate("hello", null, null);
    }

    @Test
    void whenBothProvidersFail_throwsProviderException() {
        when(geminiProvider.generate(any(), any(), any()))
                .thenThrow(new ProviderException("Gemini is down"));
        when(ollamaProvider.generate(any(), any(), any()))
                .thenThrow(new ProviderException("Ollama is down too"));

        assertThatThrownBy(() -> failoverLlmProvider.generate("hello", null, null))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("Both primary and fallback providers failed");
    }
}
