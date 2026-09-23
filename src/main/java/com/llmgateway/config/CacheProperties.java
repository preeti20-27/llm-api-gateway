package com.llmgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds "cache.ttl-seconds" from application.yml. Plain YAML, not env-driven —
 * operational tuning, same reasoning as RateLimitProperties.
 */
@ConfigurationProperties(prefix = "cache")
public record CacheProperties(int ttlSeconds) {
}
