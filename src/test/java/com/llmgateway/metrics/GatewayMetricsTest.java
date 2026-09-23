package com.llmgateway.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SimpleMeterRegistry is a plain, in-memory Micrometer registry — no Spring context
 * or Prometheus needed to verify metrics are recorded under the right name/tags.
 */
class GatewayMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final GatewayMetrics metrics = new GatewayMetrics(registry);

    @Test
    void recordChatRequest_incrementsCounterAndRecordsLatency() {
        metrics.recordChatRequest("gemini", false, 120);

        double count = registry.get("llm.gateway.chat.requests")
                .tag("provider", "gemini")
                .tag("cached", "false")
                .counter()
                .count();
        assertThat(count).isEqualTo(1.0);

        long timerCount = registry.get("llm.gateway.chat.latency")
                .tag("provider", "gemini")
                .timer()
                .count();
        assertThat(timerCount).isEqualTo(1);
    }

    @Test
    void recordCacheHitAndMiss_incrementDistinctCounters() {
        metrics.recordCacheHit();
        metrics.recordCacheHit();
        metrics.recordCacheMiss();

        assertThat(registry.get("llm.gateway.cache.result").tag("result", "hit").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("llm.gateway.cache.result").tag("result", "miss").counter().count()).isEqualTo(1.0);
    }

    @Test
    void recordRateLimitRejection_incrementsCounter() {
        metrics.recordRateLimitRejection();
        metrics.recordRateLimitRejection();

        assertThat(registry.get("llm.gateway.ratelimit.rejections").counter().count()).isEqualTo(2.0);
    }

    @Test
    void recordProviderFallback_incrementsCounter() {
        metrics.recordProviderFallback();

        assertThat(registry.get("llm.gateway.provider.fallback").counter().count()).isEqualTo(1.0);
    }
}
