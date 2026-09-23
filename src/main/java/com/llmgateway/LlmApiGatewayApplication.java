package com.llmgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point. @ConfigurationPropertiesScan picks up @ConfigurationProperties
 * classes (like GeminiProperties) anywhere under this package without needing
 * an explicit @EnableConfigurationProperties on each one.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LlmApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(LlmApiGatewayApplication.class, args);
    }
}
