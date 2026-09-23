package com.llmgateway.controller;

import com.llmgateway.dto.CreateApiKeyRequest;
import com.llmgateway.dto.CreateApiKeyResponse;
import com.llmgateway.service.ApiKeyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminKeyController {

    private final ApiKeyService apiKeyService;

    public AdminKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping("/keys")
    public ResponseEntity<CreateApiKeyResponse> createKey(@Valid @RequestBody CreateApiKeyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(apiKeyService.createKey(request));
    }
}
