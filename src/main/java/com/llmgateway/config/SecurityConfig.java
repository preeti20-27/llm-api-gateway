package com.llmgateway.config;

import com.llmgateway.metrics.GatewayMetrics;
import com.llmgateway.ratelimit.RateLimitFilter;
import com.llmgateway.ratelimit.RateLimiter;
import com.llmgateway.repository.ApiKeyRepository;
import com.llmgateway.security.AdminTokenAuthenticationFilter;
import com.llmgateway.security.ApiKeyAuthenticationFilter;
import com.llmgateway.security.ApiKeyHasher;
import com.llmgateway.security.JsonAuthenticationEntryPoint;
import com.llmgateway.security.JsonErrorResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Two separate filter chains, each scoped to its own URL space via securityMatcher —
 * the standard Spring Security pattern for "different auth mechanism per area of the
 * app" (see the reference docs' "Multiple HttpSecurity instances"). /admin/** uses a
 * single shared-secret header; /v1/** uses per-caller API keys. @Order picks which
 * chain Spring Security tries to match a request against first — it must be explicit
 * here since neither chain otherwise implies an order.
 */
@Configuration
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http,
                                                          AdminSecurityProperties adminSecurityProperties,
                                                          JsonAuthenticationEntryPoint entryPoint) throws Exception {
        AdminTokenAuthenticationFilter adminTokenAuthenticationFilter =
                new AdminTokenAuthenticationFilter(adminSecurityProperties, entryPoint);

        http
                .securityMatcher("/admin/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("ADMIN"))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                .addFilterBefore(adminTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                                        ApiKeyRepository apiKeyRepository,
                                                        ApiKeyHasher apiKeyHasher,
                                                        JsonAuthenticationEntryPoint entryPoint,
                                                        RateLimiter rateLimiter,
                                                        RateLimitProperties rateLimitProperties,
                                                        JsonErrorResponseWriter errorResponseWriter,
                                                        GatewayMetrics gatewayMetrics) throws Exception {
        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter =
                new ApiKeyAuthenticationFilter(apiKeyRepository, apiKeyHasher, entryPoint);
        RateLimitFilter rateLimitFilter =
                new RateLimitFilter(rateLimiter, rateLimitProperties, errorResponseWriter, gatewayMetrics);

        http
                // Stateless API authenticated by a header, not a browser session. CSRF
                // protection exists to stop a browser silently replaying a victim's
                // session cookie — there's no session or cookie here, so it's irrelevant
                // (and would only break non-browser clients that can't fetch a token).
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Phase 6 scope note: actuator (health/metrics/prometheus) is
                        // wide open, like /admin/keys was before Phase 2's follow-up —
                        // fine for local dev and this project's scope, but production
                        // would want this on a separate, internal-only management port
                        // (management.server.port), not exposed on the public API port.
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/v1/**").authenticated()
                        .anyRequest().denyAll()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Runs after API-key auth: it needs to know who the caller is (and
                // their tier) before it can look up the right bucket.
                .addFilterAfter(rateLimitFilter, ApiKeyAuthenticationFilter.class);

        return http.build();
    }
}
