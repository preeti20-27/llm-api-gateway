package com.llmgateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Smoke test: does the full application context start up with no wiring errors?
 * No network calls happen at startup, so this needs no real Gemini API key — but as
 * of Phase 2, JPA + Flyway connect to a real datasource on startup, so a real
 * Postgres (via Testcontainers) is required to bring the context up at all.
 */
@SpringBootTest
@Testcontainers
class LlmApiGatewayApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoads() {
    }
}
