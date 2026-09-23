package com.llmgateway.dto;

import com.llmgateway.entity.ApiKeyTier;

import java.time.Instant;
import java.util.UUID;

/**
 * @param apiKey the raw key — this is the only time it's ever shown. Only its hash
 *               is stored, so if this response is lost, the key cannot be recovered;
 *               a new one must be created instead.
 */
public record CreateApiKeyResponse(
        UUID id,
        String name,
        ApiKeyTier tier,
        String apiKey,
        Instant createdAt
) {
}
