package com.llmgateway.security;

import com.llmgateway.config.AdminSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Guards /admin/** with a single shared secret, checked against X-Admin-Token.
 * <p>
 * Uses {@link MessageDigest#isEqual(byte[], byte[])} rather than String.equals() or
 * Arrays.equals(): those short-circuit and return false at the first mismatched byte,
 * so how long the comparison takes leaks how many leading bytes were correct — an
 * attacker measuring response times could recover the token one byte at a time.
 * MessageDigest.isEqual always compares every byte (constant-time), specifically to
 * close that timing side-channel; it's the standard JDK-provided way to compare
 * secrets safely.
 * <p>
 * Fails closed: if ADMIN_TOKEN was never configured, every admin request is rejected
 * rather than the comparison accidentally succeeding against an empty string.
 * <p>
 * Not a @Component — see ApiKeyAuthenticationFilter's Javadoc for why; SecurityConfig
 * constructs this directly.
 */
public class AdminTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

    private final AdminSecurityProperties adminSecurityProperties;
    private final JsonAuthenticationEntryPoint entryPoint;

    public AdminTokenAuthenticationFilter(AdminSecurityProperties adminSecurityProperties,
                                           JsonAuthenticationEntryPoint entryPoint) {
        this.adminSecurityProperties = adminSecurityProperties;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String providedToken = request.getHeader(ADMIN_TOKEN_HEADER);

        if (isValid(providedToken)) {
            SecurityContextHolder.getContext().setAuthentication(new AdminAuthenticationToken());
            filterChain.doFilter(request, response);
        } else {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, new BadCredentialsException("Missing or invalid admin token"));
        }
    }

    private boolean isValid(String providedToken) {
        String expectedToken = adminSecurityProperties.token();

        if (!StringUtils.hasText(expectedToken) || !StringUtils.hasText(providedToken)) {
            return false;
        }

        return MessageDigest.isEqual(
                providedToken.getBytes(StandardCharsets.UTF_8),
                expectedToken.getBytes(StandardCharsets.UTF_8)
        );
    }
}
