package com.llmgateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmgateway.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Writes the same ErrorResponse JSON shape GlobalExceptionHandler uses, for the
 * places in the servlet filter chain that reject a request before it ever reaches a
 * controller — and so never reach @RestControllerAdvice: authentication failures
 * (JsonAuthenticationEntryPoint) and, from Phase 3, rate-limit rejections
 * (RateLimitFilter). Pulled out once two call sites needed the identical
 * status/body-writing logic.
 */
@Component
public class JsonErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public JsonErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, HttpServletRequest request,
                       int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse body = new ErrorResponse(Instant.now(), status, error, message, request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
