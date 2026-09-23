package com.llmgateway.cache;

/**
 * What actually gets cached — deliberately NOT the full ChatResponse. latencyMs is
 * specific to one request (a cache hit's latency is nothing like the original call's)
 * and cached is contextual (true on a hit, but this same stored value produces
 * cached=false the first time it's created) — both are computed fresh by ChatService
 * on every call, hit or miss.
 */
public record CachedChatResult(String text, int tokensUsed, String provider) {
}
