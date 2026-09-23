package com.llmgateway.exception;

/**
 * Thrown when an upstream LLM provider (Gemini, Ollama, ...) fails to produce a
 * usable response — network error, non-2xx status, or an empty/blocked result.
 */
public class ProviderException extends RuntimeException {

    public ProviderException(String message) {
        super(message);
    }

    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
