package com.llmgateway.dto;

import com.llmgateway.entity.ApiKeyTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateApiKeyRequest(
        @NotBlank(message = "name must not be blank")
        String name,

        @NotNull(message = "tier must be FREE or PRO")
        ApiKeyTier tier
) {
}
