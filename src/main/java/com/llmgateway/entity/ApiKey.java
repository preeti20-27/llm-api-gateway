package com.llmgateway.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A stored API key. Only the SHA-256 hash of the raw key is ever persisted — see
 * {@link com.llmgateway.security.ApiKeyHasher} — so a database leak alone doesn't
 * expose usable credentials.
 */
@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "key_hash", nullable = false, unique = true)
    private String keyHash;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApiKeyTier tier;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean active;

    protected ApiKey() {
        // required by JPA/Hibernate; not for application use
    }

    public ApiKey(String keyHash, String name, ApiKeyTier tier) {
        this.keyHash = keyHash;
        this.name = name;
        this.tier = tier;
        this.createdAt = Instant.now();
        this.active = true;
    }

    public UUID getId() {
        return id;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public String getName() {
        return name;
    }

    public ApiKeyTier getTier() {
        return tier;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isActive() {
        return active;
    }
}
