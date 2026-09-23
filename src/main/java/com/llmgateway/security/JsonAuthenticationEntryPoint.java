package com.llmgateway.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Runs whenever Spring Security rejects a request — shared by both auth mechanisms
 * in this app (ApiKeyAuthenticationFilter for /v1/**, AdminTokenAuthenticationFilter
 * for /admin/**). This happens inside the security filter chain, upstream of any
 * controller, so GlobalExceptionHandler's @RestControllerAdvice never sees it. The
 * message comes from whichever filter raised the AuthenticationException, so it
 * stays accurate for both "invalid API key" and "invalid admin token" cases.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final JsonErrorResponseWriter errorResponseWriter;

    public JsonAuthenticationEntryPoint(JsonErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        errorResponseWriter.write(response, request, HttpServletResponse.SC_UNAUTHORIZED,
                "Unauthorized", authException.getMessage());
    }
}
