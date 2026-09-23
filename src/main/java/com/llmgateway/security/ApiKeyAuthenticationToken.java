package com.llmgateway.security;

import com.llmgateway.entity.ApiKey;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * Populated into the SecurityContext once a request's X-API-Key has been verified.
 * The principal is the {@link ApiKey} entity itself (not just its id/name) so later
 * phases — rate limiting by tier, usage logging by key — can read it straight off
 * the SecurityContext instead of hitting the database again.
 */
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final ApiKey apiKey;

    public ApiKeyAuthenticationToken(ApiKey apiKey) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + apiKey.getTier().name())));
        this.apiKey = apiKey;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        // The raw key lived only long enough to be hashed and looked up (see
        // ApiKeyAuthenticationFilter) — there is nothing left to expose here.
        return null;
    }

    @Override
    public Object getPrincipal() {
        return apiKey;
    }
}
