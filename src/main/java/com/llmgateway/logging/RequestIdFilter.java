package com.llmgateway.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Runs before every other filter, including Spring Security's own chain (@Order
 * places it earlier than SecurityProperties.DEFAULT_FILTER_ORDER, where Spring
 * Security's filter chain registers) — so every log line for a request, including
 * ones that never make it past authentication or validation, carries the same
 * request ID.
 * <p>
 * Deliberately a plain @Component here, unlike ApiKeyAuthenticationFilter/
 * RateLimitFilter: those are manually wired into HttpSecurity to avoid Spring Boot's
 * generic Filter auto-registration running them a second time outside the security
 * chain. This filter has no such conflict — global, automatic registration for
 * every request Boot's embedded server handles is exactly what it's for.
 * <p>
 * Reuses an incoming X-Request-Id header when a caller or upstream proxy already
 * set one (so a request can be correlated across services); generates a fresh UUID
 * otherwise. Echoed back as a response header so a client can match its own logs
 * against the gateway's. MDC is thread-local and cleared in a finally block — it
 * must never leak into whatever request a reused (or, with virtual threads, freshly
 * created but pooled-adjacent) thread handles next.
 */
@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER - 10)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        String requestId = StringUtils.hasText(incoming) ? incoming : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
