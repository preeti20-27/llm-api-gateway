package com.llmgateway.security;

import com.llmgateway.util.Sha256;
import org.springframework.stereotype.Component;

/**
 * Hashes raw API keys with SHA-256 before they're stored or looked up. This is
 * plain hashing, not password hashing (no bcrypt/Argon2, no per-key salt) —
 * deliberately. See the Phase 2 write-up for why that's the right call here and
 * how it differs from hashing user passwords.
 * <p>
 * Kept as its own component (rather than callers using Sha256 directly) so this
 * call site stays easy to find, name, and mock in tests — even though the
 * implementation is now just a one-line delegation.
 */
@Component
public class ApiKeyHasher {

    public String hash(String rawKey) {
        return Sha256.hex(rawKey);
    }
}
