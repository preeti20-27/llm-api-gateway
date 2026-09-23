package com.llmgateway.cache;

import com.llmgateway.util.Sha256;
import org.springframework.stereotype.Component;

/**
 * Cache key = SHA-256 of (model + normalized prompt + maxTokens). Deliberately does
 * NOT include the caller's API key: the cache is shared across every caller, so an
 * identical prompt from a different customer is served from cache too. That's a
 * real trade-off (see the Phase 4 write-up for the multi-tenancy angle) — cheaper
 * and faster, at the cost of one caller's prompt being able to warm another's cache
 * hit.
 */
@Component
public class CacheKeyGenerator {

    public String generate(String model, String prompt, Integer maxTokens) {
        String normalizedPrompt = prompt.trim().replaceAll("\\s+", " ");
        String modelPart = model != null ? model : "";
        String maxTokensPart = maxTokens != null ? maxTokens.toString() : "";

        return Sha256.hex(modelPart + "|" + normalizedPrompt + "|" + maxTokensPart);
    }
}
