package com.llmgateway.config;

import com.llmgateway.security.ApiKeyAuthenticationEntryPoint;
import com.llmgateway.security.ApiKeyAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                     ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
                                                     ApiKeyAuthenticationEntryPoint entryPoint) throws Exception {
        http
                // Stateless API authenticated by a header, not a browser session. CSRF
                // protection exists to stop a browser silently replaying a victim's
                // session cookie — there's no session or cookie here, so it's irrelevant
                // (and would only break non-browser clients that can't fetch a token).
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Phase 2 scope note: /admin/** is intentionally NOT locked down yet.
                        // Anyone who can reach this service can mint API keys right now — that's
                        // a real gap, not an oversight, and it's called out in the phase write-up
                        // as a follow-up rather than silently left for later.
                        .requestMatchers("/admin/**").permitAll()
                        .requestMatchers("/v1/**").authenticated()
                        .anyRequest().denyAll()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
