package com.llmgateway.provider;

import com.llmgateway.metrics.GatewayMetrics;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * The Gemini call wrapped in Resilience4j's circuit breaker + retry + timeout, with
 * a fallback to Ollama. Deliberately its own bean, separate from FailoverLlmProvider
 * — Resilience4j's @CircuitBreaker/@Retry/@TimeLimiter are Spring AOP method
 * interceptors, meaning they only take effect on a call that actually goes through
 * the bean's proxy. A call from FailoverLlmProvider.generate() to
 * this.callGeminiWithResilience() on the SAME object (self-invocation) would bypass
 * the proxy entirely and silently skip all three decorators — a well-known Spring
 * AOP pitfall. Calling call() on this separate, injected bean is a genuine
 * cross-bean call through its proxy, so the decorators actually run.
 */
@Component
public class ResilientGeminiCaller {

    private static final Logger log = LoggerFactory.getLogger(ResilientGeminiCaller.class);

    private final GeminiProvider geminiProvider;
    private final OllamaProvider ollamaProvider;
    private final ExecutorService virtualThreadExecutor;
    private final GatewayMetrics gatewayMetrics;

    public ResilientGeminiCaller(GeminiProvider geminiProvider, OllamaProvider ollamaProvider,
                                  ExecutorService virtualThreadExecutor, GatewayMetrics gatewayMetrics) {
        this.geminiProvider = geminiProvider;
        this.ollamaProvider = ollamaProvider;
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.gatewayMetrics = gatewayMetrics;
    }

    /**
     * Thresholds live in application.yml under resilience4j.circuitbreaker/retry/
     * timelimiter.instances.gemini — see the Phase 5 write-up for what each one means
     * and how to tune them. All three point at the same fallback: whichever one
     * trips first, the caller gets an answer from Ollama instead of an error.
     */
    @CircuitBreaker(name = "gemini", fallbackMethod = "fallbackToOllama")
    @Retry(name = "gemini", fallbackMethod = "fallbackToOllama")
    @TimeLimiter(name = "gemini", fallbackMethod = "fallbackToOllama")
    public CompletableFuture<LlmProviderResponse> call(String prompt, String model, Integer maxTokens) {
        return CompletableFuture.supplyAsync(() -> geminiProvider.generate(prompt, model, maxTokens), virtualThreadExecutor);
    }

    /**
     * Signature is fixed by Resilience4j's convention: same parameters as the
     * annotated method, plus the Throwable that triggered the fallback.
     */
    private CompletableFuture<LlmProviderResponse> fallbackToOllama(String prompt, String model, Integer maxTokens,
                                                                      Throwable throwable) {
        gatewayMetrics.recordProviderFallback();
        log.warn("falling back to ollama",
                StructuredArguments.kv("reason", throwable.getClass().getSimpleName()),
                StructuredArguments.kv("message", throwable.getMessage()));

        return CompletableFuture.supplyAsync(() -> ollamaProvider.generate(prompt, model, maxTokens), virtualThreadExecutor);
    }
}
