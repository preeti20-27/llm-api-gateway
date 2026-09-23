package com.llmgateway.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmgateway.config.CacheProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Against a real Redis — no Spring context. The connection is built once in
 * @BeforeAll and warmed up immediately with a throwaway call; see RateLimiterTest's
 * Javadoc for why a fresh connection's first command shouldn't be the one a timing
 * assertion (entryExpiresAfterTtl) depends on.
 */
@Testcontainers
class ResponseCacheTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static StringRedisTemplate redisTemplate;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ResponseCache cache = new ResponseCache(redisTemplate, OBJECT_MAPPER, new CacheProperties(300));

    @BeforeAll
    static void setUpSharedConnection() {
        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        new ResponseCache(redisTemplate, OBJECT_MAPPER, new CacheProperties(300)).get("warmup");
    }

    private String uniqueKey() {
        return "test:" + UUID.randomUUID();
    }

    @Test
    void put_thenGet_returnsSameResult() {
        String key = uniqueKey();
        CachedChatResult result = new CachedChatResult("hello there", 10, "gemini");

        cache.put(key, result);

        assertThat(cache.get(key)).contains(result);
    }

    @Test
    void get_onMiss_returnsEmpty() {
        assertThat(cache.get(uniqueKey())).isEmpty();
    }

    @Test
    void entryExpiresAfterTtl() throws InterruptedException {
        ResponseCache shortTtlCache = new ResponseCache(redisTemplate, OBJECT_MAPPER, new CacheProperties(1));
        String key = uniqueKey();
        shortTtlCache.put(key, new CachedChatResult("hi", 1, "gemini"));

        assertThat(shortTtlCache.get(key)).isPresent();

        Thread.sleep(1100);

        assertThat(shortTtlCache.get(key)).isEmpty();
    }
}
