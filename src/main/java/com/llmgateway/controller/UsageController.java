package com.llmgateway.controller;

import com.llmgateway.dto.UsageSummaryResponse;
import com.llmgateway.entity.ApiKey;
import com.llmgateway.service.UsageLogService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class UsageController {

    private final UsageLogService usageLogService;

    public UsageController(UsageLogService usageLogService) {
        this.usageLogService = usageLogService;
    }

    /**
     * Scoped to the calling key by construction: apiKey comes from the
     * SecurityContext (populated by ApiKeyAuthenticationFilter), so there's no way
     * to pass a different key's id — a caller can only ever see its own usage.
     */
    @GetMapping("/usage")
    public UsageSummaryResponse usage(@AuthenticationPrincipal ApiKey apiKey) {
        return usageLogService.summarize(apiKey.getId());
    }
}
