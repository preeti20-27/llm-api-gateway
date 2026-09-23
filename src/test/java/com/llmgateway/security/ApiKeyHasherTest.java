package com.llmgateway.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyHasherTest {

    private final ApiKeyHasher hasher = new ApiKeyHasher();

    @Test
    void hash_isDeterministic() {
        assertThat(hasher.hash("sk-abc123")).isEqualTo(hasher.hash("sk-abc123"));
    }

    @Test
    void hash_differsForDifferentInput() {
        assertThat(hasher.hash("sk-abc123")).isNotEqualTo(hasher.hash("sk-abc124"));
    }

    @Test
    void hash_isA64CharacterHexString() {
        String hash = hasher.hash("sk-anything");

        assertThat(hash).hasSize(64); // SHA-256 -> 32 bytes -> 64 hex chars
        assertThat(hash).matches("[0-9a-f]{64}");
    }
}
