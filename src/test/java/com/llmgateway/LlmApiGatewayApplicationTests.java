package com.llmgateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: does the full application context start up with no wiring errors?
 * No network calls happen at startup, so this needs no real Gemini API key.
 */
@SpringBootTest
class LlmApiGatewayApplicationTests {

    @Test
    void contextLoads() {
    }
}
