package com.llmgateway.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashes raw API keys with SHA-256 before they're stored or looked up. This is
 * plain hashing, not password hashing (no bcrypt/Argon2, no per-key salt) —
 * deliberately. See the Phase 2 write-up for why that's the right call here and
 * how it differs from hashing user passwords.
 */
@Component
public class ApiKeyHasher {

    private static final String ALGORITHM = "SHA-256";

    public String hash(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashBytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by every JDK implementation (JCA standard algorithm
            // names spec) — this branch is unreachable in practice.
            throw new IllegalStateException("SHA-256 MessageDigest not available", e);
        }
    }
}
