package com.llmgateway.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * Populated into the SecurityContext once a request's X-Admin-Token has been
 * verified. There's no entity behind it (unlike ApiKeyAuthenticationToken) — the
 * admin token isn't tied to a stored row, just a single shared secret from config.
 */
public class AdminAuthenticationToken extends AbstractAuthenticationToken {

    public AdminAuthenticationToken() {
        super(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return "admin";
    }
}
