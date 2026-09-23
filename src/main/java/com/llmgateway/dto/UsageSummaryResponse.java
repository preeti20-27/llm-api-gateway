package com.llmgateway.dto;

public record UsageSummaryResponse(long totalRequests, long totalTokens, double estimatedCostUsd, long cacheHits) {
}
