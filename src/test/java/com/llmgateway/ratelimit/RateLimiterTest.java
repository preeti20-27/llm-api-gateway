package com.llmgateway.ratelimit;

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
 * Exercises the token_bucket.lua script directly through RateLimiter, against a real
 * Redis — no Spring context, no Postgres, no HTTP layer. This is the fastest,
 * narrowest place to verify the algorithm itself (capacity, denial, refill-over-time,
 * per-key isolation); RateLimitIntegrationTest separately verifies it's wired
 * correctly into the actual HTTP request path (headers, status codes).
 * <p>
 * The connection is built once in @BeforeAll, not per test method, and a throwaway
 * call warms it up immediately. This matters for refillsTokensAfterWindowElapses:
 * a fresh Lettuce connection's first command pays a one-time TCP/handshake cost
 * (which, over a Testcontainers-mapped port, is occasionally hundreds of
 * milliseconds) — measured inside "elapsed time since last refill", that cost could
 * eat into a capacity-1/1-token-per-second bucket's margin and make the very next
 * call look like real elapsed time. The production RateLimiter never hits this: its
 * connection pool is warmed once at application startup, not once per request.
 */
@Testcontainers
class RateLimiterTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static RateLimiter rateLimiter;

    @BeforeAll
    static void setUpSharedConnection() {
        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();

        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        rateLimiter = new RateLimiter(redisTemplate);
        rateLimiter.checkAndConsume("warmup", 1, 1.0); // pays connection setup cost once, here
    }

    private String uniqueKey() {
        return "test:" + UUID.randomUUID();
    }

    @Test
    void allowsRequestsUpToCapacityThenDenies() {
        String key = uniqueKey();

        for (int i = 0; i < 3; i++) {
            RateLimitResult result = rateLimiter.checkAndConsume(key, 3, 1.0);
            assertThat(result.allowed()).isTrue();
        }

        RateLimitResult denied = rateLimiter.checkAndConsume(key, 3, 1.0);

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.remaining()).isZero();
        assertThat(denied.retryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    void remainingCountDecreasesByOnePerAllowedRequest() {
        String key = uniqueKey();

        assertThat(rateLimiter.checkAndConsume(key, 5, 1.0).remaining()).isEqualTo(4);
        assertThat(rateLimiter.checkAndConsume(key, 5, 1.0).remaining()).isEqualTo(3);
        assertThat(rateLimiter.checkAndConsume(key, 5, 1.0).remaining()).isEqualTo(2);
    }

    @Test
    void refillsTokensAfterWindowElapses() throws InterruptedException {
        String key = uniqueKey();

        // capacity 1, refill rate 1 token/second -> fully refills in ~1s
        assertThat(rateLimiter.checkAndConsume(key, 1, 1.0).allowed()).isTrue();
        assertThat(rateLimiter.checkAndConsume(key, 1, 1.0).allowed()).isFalse();

        Thread.sleep(1100);

        assertThat(rateLimiter.checkAndConsume(key, 1, 1.0).allowed()).isTrue();
    }

    @Test
    void differentKeysHaveIndependentBuckets() {
        String keyA = uniqueKey();
        String keyB = uniqueKey();

        rateLimiter.checkAndConsume(keyA, 1, 1.0); // exhausts key A's single token
        RateLimitResult resultB = rateLimiter.checkAndConsume(keyB, 1, 1.0);

        assertThat(resultB.allowed()).isTrue();
    }
}
