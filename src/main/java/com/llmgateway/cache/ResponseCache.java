package com.llmgateway.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmgateway.config.CacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed cache for chat responses, keyed by CacheKeyGenerator's hash. TTL
 * expiry (rather than explicit invalidation) is the whole invalidation strategy
 * here — see the Phase 4 write-up for why that trade-off is the right one for this
 * endpoint.
 */
@Component
public class ResponseCache {

    private static final String KEY_PREFIX = "chat-cache:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheProperties cacheProperties;

    public ResponseCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                          CacheProperties cacheProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheProperties = cacheProperties;
    }

    public Optional<CachedChatResult> get(String cacheKey) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + cacheKey);
        if (json == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(json, CachedChatResult.class));
        } catch (JsonProcessingException e) {
            // A cache entry that no longer deserializes (e.g. its shape changed in a
            // deploy) is treated as a miss, not a failure — caching is an optimization,
            // never something a request should fail over.
            return Optional.empty();
        }
    }

    public void put(String cacheKey, CachedChatResult result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(KEY_PREFIX + cacheKey, json, Duration.ofSeconds(cacheProperties.ttlSeconds()));
        } catch (JsonProcessingException e) {
            // Same reasoning as above: swallow and continue uncached rather than fail
            // a request that already has a perfectly good answer to return.
        }
    }
}
