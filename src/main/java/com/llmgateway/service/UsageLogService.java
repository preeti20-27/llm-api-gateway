package com.llmgateway.service;

import com.llmgateway.config.PricingProperties;
import com.llmgateway.dto.UsageSummaryResponse;
import com.llmgateway.entity.UsageLog;
import com.llmgateway.repository.UsageLogRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UsageLogService {

    private final UsageLogRepository usageLogRepository;
    private final PricingProperties pricingProperties;

    public UsageLogService(UsageLogRepository usageLogRepository, PricingProperties pricingProperties) {
        this.usageLogRepository = usageLogRepository;
        this.pricingProperties = pricingProperties;
    }

    /**
     * Fire-and-forget from the caller's point of view: ChatService calls this after
     * it already has a response ready to return, so the client is never kept
     * waiting on a database write. See the Phase 4 write-up for why this is safe to
     * do without also making the response itself unreliable.
     */
    @Async
    public void recordAsync(UUID keyId, String provider, int tokens, boolean cached, long latencyMs) {
        double estimatedCost = pricingProperties.costFor(provider, tokens);
        usageLogRepository.save(new UsageLog(keyId, provider, tokens, estimatedCost, latencyMs, cached));
    }

    public UsageSummaryResponse summarize(UUID keyId) {
        long totalRequests = usageLogRepository.countByKeyId(keyId);
        long totalTokens = usageLogRepository.sumTokensByKeyId(keyId);
        double estimatedCostUsd = usageLogRepository.sumEstimatedCostByKeyId(keyId);
        long cacheHits = usageLogRepository.countByKeyIdAndCachedTrue(keyId);

        return new UsageSummaryResponse(totalRequests, totalTokens, estimatedCostUsd, cacheHits);
    }
}
