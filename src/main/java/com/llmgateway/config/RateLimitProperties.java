package com.llmgateway.config;

import com.llmgateway.entity.ApiKeyTier;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds "rate-limit.free.*" / "rate-limit.pro.*" from application.yml. Limits are
 * plain YAML values (not env-driven like secrets) — they're operational tuning
 * knobs, not something that differs between environments in a way .env needs to own.
 */
@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(TierLimit free, TierLimit pro) {

    public record TierLimit(int capacity, int windowSeconds) {
        public double refillRatePerSecond() {
            return (double) capacity / windowSeconds;
        }
    }

    public TierLimit forTier(ApiKeyTier tier) {
        return switch (tier) {
            case FREE -> free;
            case PRO -> pro;
        };
    }
}
