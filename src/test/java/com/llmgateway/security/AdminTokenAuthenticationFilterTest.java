package com.llmgateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmgateway.config.AdminSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Calls doFilterInternal directly (accessible here — protected members are visible
 * within the same package) rather than going through Spring or MockMvc: this is a
 * pure unit test of the filter's own logic — constant-time comparison and fail-closed
 * behavior — independent of the database, Docker, or the rest of the app. The
 * end-to-end wiring (real SecurityConfig, real HTTP status/JSON) is covered by
 * AdminTokenAuthenticationIntegrationTest.
 */
class AdminTokenAuthenticationFilterTest {

    // findAndRegisterModules() picks up jackson-datatype-jsr310 (on the classpath via
    // spring-boot-starter-web) so Instant serializes — plain `new ObjectMapper()` can't
    // serialize ErrorResponse.timestamp without it. Spring Boot's autoconfigured
    // ObjectMapper bean does this same discovery for you; here it's done by hand since
    // this test builds the entry point outside of a Spring context on purpose.
    private final JsonAuthenticationEntryPoint entryPoint =
            new JsonAuthenticationEntryPoint(new JsonErrorResponseWriter(new ObjectMapper().findAndRegisterModules()));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void withCorrectToken_authenticatesAsAdminAndContinuesChain() throws Exception {
        AdminTokenAuthenticationFilter filter =
                new AdminTokenAuthenticationFilter(new AdminSecurityProperties("correct-token"), entryPoint);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AdminTokenAuthenticationFilter.ADMIN_TOKEN_HEADER, "correct-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isInstanceOf(AdminAuthenticationToken.class);
    }

    @Test
    void withWrongToken_rejectsWith401AndNeverCallsChain() throws Exception {
        AdminTokenAuthenticationFilter filter =
                new AdminTokenAuthenticationFilter(new AdminSecurityProperties("correct-token"), entryPoint);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AdminTokenAuthenticationFilter.ADMIN_TOKEN_HEADER, "wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void withMissingHeader_rejectsWith401() throws Exception {
        AdminTokenAuthenticationFilter filter =
                new AdminTokenAuthenticationFilter(new AdminSecurityProperties("correct-token"), entryPoint);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void withTokenNotConfigured_failsClosedEvenAgainstABlankProvidedToken() throws Exception {
        // Mirrors production: ADMIN_TOKEN unset binds to "" via ${ADMIN_TOKEN:}.
        AdminTokenAuthenticationFilter filter =
                new AdminTokenAuthenticationFilter(new AdminSecurityProperties(""), entryPoint);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AdminTokenAuthenticationFilter.ADMIN_TOKEN_HEADER, "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
