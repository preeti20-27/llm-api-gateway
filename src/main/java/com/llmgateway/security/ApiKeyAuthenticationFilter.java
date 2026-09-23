package com.llmgateway.security;

import com.llmgateway.entity.ApiKey;
import com.llmgateway.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Runs once per request, before Spring Security's authorization check. Three outcomes:
 * <ul>
 *   <li>no X-API-Key header: leave the request unauthenticated and continue — whether
 *       that's allowed is decided afterwards by SecurityConfig's authorizeHttpRequests
 *       rules (e.g. /admin/** doesn't require one, /v1/** does).</li>
 *   <li>header present and matches an active key: populate the SecurityContext, continue.</li>
 *   <li>header present but doesn't match: reject immediately with 401, even for an
 *       endpoint that wouldn't otherwise require auth — presenting a bad key is always
 *       treated as an error, never silently ignored.</li>
 * </ul>
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyHasher apiKeyHasher;
    private final ApiKeyAuthenticationEntryPoint entryPoint;

    public ApiKeyAuthenticationFilter(ApiKeyRepository apiKeyRepository,
                                       ApiKeyHasher apiKeyHasher,
                                       ApiKeyAuthenticationEntryPoint entryPoint) {
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyHasher = apiKeyHasher;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String rawKey = request.getHeader(API_KEY_HEADER);

        if (StringUtils.hasText(rawKey)) {
            Optional<ApiKey> found = apiKeyRepository.findByKeyHashAndActiveTrue(apiKeyHasher.hash(rawKey));

            if (found.isPresent()) {
                SecurityContextHolder.getContext().setAuthentication(new ApiKeyAuthenticationToken(found.get()));
            } else {
                SecurityContextHolder.clearContext();
                entryPoint.commence(request, response, new BadCredentialsException("Invalid API key"));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
