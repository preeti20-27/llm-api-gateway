package com.llmgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds "admin.token" from application.yml, which reads ADMIN_TOKEN from the
 * environment. There is no default value baked in — an unset token means every
 * admin request is rejected (see AdminTokenAuthenticationFilter), not that admin
 * endpoints are accidentally left open.
 */
@ConfigurationProperties(prefix = "admin")
public record AdminSecurityProperties(String token) {
}
