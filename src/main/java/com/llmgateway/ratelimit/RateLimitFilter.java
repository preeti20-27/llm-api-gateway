package com.llmgateway.ratelimit;

import com.llmgateway.config.RateLimitProperties;
import com.llmgateway.entity.ApiKey;
import com.llmgateway.metrics.GatewayMetrics;
import com.llmgateway.security.ApiKeyAuthenticationToken;
import com.llmgateway.security.JsonErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Enforces a per-API-key token-bucket limit. Must run AFTER ApiKeyAuthenticationFilter
 * in the chain (see SecurityConfig's addFilterAfter) — it needs the caller's identity
 * and tier, which only exist in the SecurityContext once that filter has already run.
 * <p>
 * Not a @Component — see ApiKeyAuthenticationFilter's Javadoc for why; SecurityConfig
 * constructs it directly.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    public static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
    public static final String RETRY_AFTER_HEADER = "Retry-After";

    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;
    private final JsonErrorResponseWriter errorResponseWriter;
    private final GatewayMetrics gatewayMetrics;

    public RateLimitFilter(RateLimiter rateLimiter,
                            RateLimitProperties rateLimitProperties,
                            JsonErrorResponseWriter errorResponseWriter,
                            GatewayMetrics gatewayMetrics) {
        this.rateLimiter = rateLimiter;
        this.rateLimitProperties = rateLimitProperties;
        this.errorResponseWriter = errorResponseWriter;
        this.gatewayMetrics = gatewayMetrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof ApiKeyAuthenticationToken apiKeyAuthentication)) {
            // No authenticated key at this point (e.g. request already lacks one) —
            // nothing to rate-limit against. Let authorizeHttpRequests handle it.
            filterChain.doFilter(request, response);
            return;
        }

        ApiKey apiKey = (ApiKey) apiKeyAuthentication.getPrincipal();
        RateLimitProperties.TierLimit limit = rateLimitProperties.forTier(apiKey.getTier());

        RateLimitResult result = rateLimiter.checkAndConsume(
                "rate-limit:" + apiKey.getId(),
                limit.capacity(),
                limit.refillRatePerSecond()
        );

        response.setHeader(RATE_LIMIT_REMAINING_HEADER, String.valueOf(result.remaining()));

        if (!result.allowed()) {
            gatewayMetrics.recordRateLimitRejection();
            log.info("rate limit exceeded",
                    StructuredArguments.kv("apiKeyId", apiKey.getId()),
                    StructuredArguments.kv("tier", apiKey.getTier()),
                    StructuredArguments.kv("retryAfterSeconds", result.retryAfterSeconds()));

            response.setHeader(RETRY_AFTER_HEADER, String.valueOf(result.retryAfterSeconds()));
            // HttpServletResponse's SC_* constants predate RFC 6585 and don't include
            // 429, so HttpStatus (Spring's own enum, not tied to the old Servlet list)
            // is used here instead of a bare magic number.
            errorResponseWriter.write(response, request, HttpStatus.TOO_MANY_REQUESTS.value(),
                    "Too Many Requests", "Rate limit exceeded for this API key");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
