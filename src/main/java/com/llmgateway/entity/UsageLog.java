package com.llmgateway.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per /v1/chat call (hit or miss). key_id is a plain UUID column, not a
 * JPA @ManyToOne to ApiKey — the write path (UsageLogService) only ever has the id
 * on hand, never needs to load or navigate the ApiKey entity, and an append-only
 * log table has no business joining back to it in the ORM layer. Referential
 * integrity is still enforced at the database level (see the Flyway migration).
 */
@Entity
@Table(name = "usage_logs")
public class UsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "key_id", nullable = false)
    private UUID keyId;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private int tokens;

    @Column(name = "estimated_cost", nullable = false)
    private double estimatedCost;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(nullable = false)
    private boolean cached;

    @Column(nullable = false)
    private Instant timestamp;

    protected UsageLog() {
        // required by JPA/Hibernate; not for application use
    }

    public UsageLog(UUID keyId, String provider, int tokens, double estimatedCost, long latencyMs, boolean cached) {
        this.keyId = keyId;
        this.provider = provider;
        this.tokens = tokens;
        this.estimatedCost = estimatedCost;
        this.latencyMs = latencyMs;
        this.cached = cached;
        this.timestamp = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getKeyId() {
        return keyId;
    }

    public String getProvider() {
        return provider;
    }

    public int getTokens() {
        return tokens;
    }

    public double getEstimatedCost() {
        return estimatedCost;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public boolean isCached() {
        return cached;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
