package com.booking.platform.graphql_gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * No-op security configuration when JWT validation is disabled.
 * Used for CI/CD pipelines, integration tests, or development without Keycloak.
 *
 * All requests are permitted without any authentication.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "security.jwt.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
