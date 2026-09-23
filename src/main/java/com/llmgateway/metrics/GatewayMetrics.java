package com.llmgateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Every custom metric this app publishes, in one place — so metric names and tag
 * keys exist as one source of truth instead of string literals scattered across
 * ChatService, RateLimitFilter, and ResilientGeminiCaller. Exported at
 * /actuator/prometheus (Prometheus auto-converts the dot-separated names below to
 * underscores, e.g. llm.gateway.chat.requests -> llm_gateway_chat_requests_total).
 */
@Component
public class GatewayMetrics {

    private final MeterRegistry meterRegistry;

    public GatewayMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Request count + latency for a completed /v1/chat call. A percentile histogram
     * (not just count/sum/max) is published so Grafana can compute p50/p95/p99 via
     * histogram_quantile() server-side, rather than relying on a single
     * client-computed percentile that can't be aggregated correctly across instances.
     */
    public void recordChatRequest(String provider, boolean cached, long latencyMs) {
        Counter.builder("llm.gateway.chat.requests")
                .tag("provider", provider)
                .tag("cached", String.valueOf(cached))
                .register(meterRegistry)
                .increment();

        Timer.builder("llm.gateway.chat.latency")
                .tag("provider", provider)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(Duration.ofMillis(latencyMs));
    }

    public void recordCacheHit() {
        recordCacheResult("hit");
    }

    public void recordCacheMiss() {
        recordCacheResult("miss");
    }

    private void recordCacheResult(String result) {
        Counter.builder("llm.gateway.cache.result")
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }

    public void recordRateLimitRejection() {
        meterRegistry.counter("llm.gateway.ratelimit.rejections").increment();
    }

    public void recordProviderFallback() {
        meterRegistry.counter("llm.gateway.provider.fallback").increment();
    }
}
