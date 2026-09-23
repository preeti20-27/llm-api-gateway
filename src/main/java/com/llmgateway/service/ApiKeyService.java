package com.llmgateway.service;

import com.llmgateway.dto.CreateApiKeyRequest;
import com.llmgateway.dto.CreateApiKeyResponse;
import com.llmgateway.entity.ApiKey;
import com.llmgateway.repository.ApiKeyRepository;
import com.llmgateway.security.ApiKeyHasher;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class ApiKeyService {

    /** Prefix makes a leaked key recognizable at a glance (same idea as Stripe's "sk_live_", GitHub's "ghp_"). */
    private static final String KEY_PREFIX = "sk-";
    private static final int RAW_KEY_BYTES = 32; // 256 bits of entropy

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyHasher apiKeyHasher;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(ApiKeyRepository apiKeyRepository, ApiKeyHasher apiKeyHasher) {
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyHasher = apiKeyHasher;
    }

    public CreateApiKeyResponse createKey(CreateApiKeyRequest request) {
        String rawKey = generateRawKey();
        String keyHash = apiKeyHasher.hash(rawKey);

        ApiKey saved = apiKeyRepository.save(new ApiKey(keyHash, request.name(), request.tier()));

        // rawKey lives only in this local variable and the response below — it is
        // never persisted, logged, or held anywhere after this method returns.
        return new CreateApiKeyResponse(saved.getId(), saved.getName(), saved.getTier(), rawKey, saved.getCreatedAt());
    }

    private String generateRawKey() {
        byte[] randomBytes = new byte[RAW_KEY_BYTES];
        secureRandom.nextBytes(randomBytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
