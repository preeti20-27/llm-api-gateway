package com.llmgateway.provider;

import com.llmgateway.exception.ProviderException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

/**
 * The LlmProvider bean the rest of the app actually depends on — ChatService's
 * constructor never changed; Spring just injects this instead of GeminiProvider
 * directly, since it's the only @Primary LlmProvider bean.
 * <p>
 * The actual circuit breaker + retry + timeout + fallback logic lives in
 * ResilientGeminiCaller, a separate bean — see its Javadoc for why that split is
 * necessary (Spring AOP self-invocation) rather than just inlined here. This class
 * is the thin synchronous adapter: LlmProvider.generate() must stay blocking (that's
 * the interface ChatService depends on), so it just blocks on the CompletableFuture
 * ResilientGeminiCaller returns — cheap on a virtual thread.
 */
@Component
@Primary
public class FailoverLlmProvider implements LlmProvider {

    private final ResilientGeminiCaller resilientGeminiCaller;

    public FailoverLlmProvider(ResilientGeminiCaller resilientGeminiCaller) {
        this.resilientGeminiCaller = resilientGeminiCaller;
    }

    @Override
    public String name() {
        // Vestigial: nothing calls this to label a response anymore.
        // LlmProviderResponse.provider() carries the actual answering provider on a
        // per-call basis (this method can't — it has no idea, per call, whether
        // Gemini or the fallback answered).
        return "gemini";
    }

    @Override
    public LlmProviderResponse generate(String prompt, String model, Integer maxTokens) {
        try {
            return resilientGeminiCaller.call(prompt, model, maxTokens).get();
        } catch (ExecutionException e) {
            throw new ProviderException("Both primary and fallback providers failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException("Interrupted while waiting for a provider response", e);
        }
    }
}
