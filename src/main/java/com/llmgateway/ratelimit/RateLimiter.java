package com.llmgateway.ratelimit;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Thin wrapper around the token_bucket.lua script: builds the arguments, invokes the
 * script (Redis runs it atomically — see the script's own comment for why that
 * matters), and turns its reply into a typed result.
 * <p>
 * Redis, not an in-memory counter, is what makes this limit correct across multiple
 * gateway instances: if each instance counted requests in its own JVM, a caller could
 * get N requests/minute per instance instead of N total by spreading requests across
 * instances (e.g. behind a load balancer). Every instance here shares the same bucket
 * because they all hit the same Redis.
 */
@Component
public class RateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> tokenBucketScript;

    public RateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = new DefaultRedisScript<>();
        this.tokenBucketScript.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
        this.tokenBucketScript.setResultType(List.class);
    }

    @SuppressWarnings("unchecked")
    public RateLimitResult checkAndConsume(String bucketKey, int capacity, double refillRatePerSecond) {
        // Sent as integer milliseconds, not fractional seconds -- see the Lua script's
        // own comment for why: epoch seconds as a float loses exactly the precision
        // this algorithm depends on.
        long nowMillis = System.currentTimeMillis();

        List<Long> reply = redisTemplate.execute(
                tokenBucketScript,
                List.of(bucketKey),
                String.valueOf(capacity),
                String.valueOf(refillRatePerSecond),
                String.valueOf(nowMillis),
                "1"
        );

        boolean allowed = reply.get(0) == 1L;
        long remaining = reply.get(1);
        long retryAfterSeconds = reply.get(2);

        return new RateLimitResult(allowed, remaining, retryAfterSeconds);
    }
}
