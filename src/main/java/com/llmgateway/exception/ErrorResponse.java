package com.llmgateway.exception;

import java.time.Instant;

/**
 * Consistent JSON shape for every error response the gateway returns.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
