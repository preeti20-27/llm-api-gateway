package com.llmgateway.repository;

import com.llmgateway.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    /**
     * Looked up on every authenticated request (see ApiKeyAuthenticationFilter), so this
     * is the one query that matters for latency — the UNIQUE constraint on key_hash gives
     * it an index for free.
     */
    Optional<ApiKey> findByKeyHashAndActiveTrue(String keyHash);
}
