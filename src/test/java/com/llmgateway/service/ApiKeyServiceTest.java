package com.llmgateway.service;

import com.llmgateway.dto.CreateApiKeyRequest;
import com.llmgateway.dto.CreateApiKeyResponse;
import com.llmgateway.entity.ApiKey;
import com.llmgateway.entity.ApiKeyTier;
import com.llmgateway.repository.ApiKeyRepository;
import com.llmgateway.security.ApiKeyHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The real ApiKeyHasher is used here (not mocked) — it's pure, fast, and using the
 * real implementation is what lets this test actually verify the hash stored in the
 * repository matches what the raw key returned to the caller hashes to.
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    private final ApiKeyHasher apiKeyHasher = new ApiKeyHasher();

    @Test
    void createKey_returnsRawKeyButStoresOnlyItsHash() {
        ApiKeyService service = new ApiKeyService(apiKeyRepository, apiKeyHasher);

        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateApiKeyResponse response = service.createKey(new CreateApiKeyRequest("ci-pipeline", ApiKeyTier.PRO));

        ArgumentCaptor<ApiKey> savedKeyCaptor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(savedKeyCaptor.capture());
        ApiKey savedKey = savedKeyCaptor.getValue();

        assertThat(response.apiKey()).startsWith("sk-");
        assertThat(response.name()).isEqualTo("ci-pipeline");
        assertThat(response.tier()).isEqualTo(ApiKeyTier.PRO);

        // the entity persisted must hold the hash, never the raw key
        assertThat(savedKey.getKeyHash()).isEqualTo(apiKeyHasher.hash(response.apiKey()));
        assertThat(savedKey.getKeyHash()).isNotEqualTo(response.apiKey());
    }

    @Test
    void createKey_generatesUniqueKeysEachTime() {
        ApiKeyService service = new ApiKeyService(apiKeyRepository, apiKeyHasher);
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateApiKeyRequest request = new CreateApiKeyRequest("app", ApiKeyTier.FREE);
        String first = service.createKey(request).apiKey();
        String second = service.createKey(request).apiKey();

        assertThat(first).isNotEqualTo(second);
    }
}
