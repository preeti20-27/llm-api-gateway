package com.llmgateway.ratelimit;

public record RateLimitResult(boolean allowed, long remaining, long retryAfterSeconds) {
}
