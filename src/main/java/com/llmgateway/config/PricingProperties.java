package com.llmgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Binds "pricing.cost-per-thousand-tokens.<provider>" from application.yml — one
 * blended rate per provider. Real providers often price prompt and completion
 * tokens separately, but LlmProviderResponse only carries one combined token count
 * right now, so a single per-1k-token rate is the honest level of precision to
 * offer; this is clearly "estimated," per the API's own field name.
 */
@ConfigurationProperties(prefix = "pricing")
public record PricingProperties(Map<String, Double> costPerThousandTokens) {

    public double costFor(String provider, int tokens) {
        double ratePerThousand = costPerThousandTokens.getOrDefault(provider, 0.0);
        return (tokens / 1000.0) * ratePerThousand;
    }
}
