package com.llmgateway.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Plain SHA-256 hex digest, shared by two unrelated call sites: ApiKeyHasher (key
 * storage, security-critical) and CacheKeyGenerator (cache keys, not
 * security-critical). Not a Spring bean — it's a pure function with no
 * configuration or state, so dependency injection buys nothing here.
 */
public final class Sha256 {

    private Sha256() {
    }

    public static String hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by every JDK implementation (JCA standard algorithm
            // names spec) — this branch is unreachable in practice.
            throw new IllegalStateException("SHA-256 MessageDigest not available", e);
        }
    }
}
