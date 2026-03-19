package com.booking.platform.common.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Secures Actuator endpoints with a least-privilege approach.
 *
 * <p>By default Spring Security locks every actuator endpoint behind HTTP Basic
 * auth — including {@code /actuator/prometheus} and {@code /actuator/health},
 * which Prometheus and load-balancers need to reach without credentials.
 *
 * <p>This config creates a {@link SecurityFilterChain} at {@code @Order(1)} that:
 * <ul>
 *   <li>Permits {@code /actuator/health}, {@code /actuator/info} and
 *       {@code /actuator/prometheus} without authentication — these are safe
 *       to expose because they contain no secrets and are needed by Prometheus
 *       and health-check probes (Kubernetes liveness/readiness).</li>
 *   <li>Denies all other actuator endpoints ({@code /actuator/env},
 *       {@code /actuator/beans}, {@code /actuator/loggers},
 *       {@code /actuator/shutdown}, etc.) — these leak internal configuration
 *       or allow runtime changes and must never be publicly accessible.</li>
 * </ul>
 *
 * <p>In production the right approach is also to serve actuator on a separate
 * internal port ({@code management.server.port=8090}) restricted at the network
 * level (firewall / Kubernetes NetworkPolicy) so only Prometheus can reach it.
 *
 * <p>Picked up automatically by all services that scan
 * {@code com.booking.platform.common} via {@code @SpringBootApplication}.
 */
@Configuration
public class ActuatorSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeHttpRequests(auth -> auth
                // ── Safe to expose publicly ────────────────────────────────
                // health  → needed by load-balancers and Kubernetes probes
                // info    → app version / build metadata (no secrets)
                // prometheus → scraped by Prometheus every 15s, no credentials
                .requestMatchers(EndpointRequest.to(
                        HealthEndpoint.class,
                        InfoEndpoint.class,
                        PrometheusScrapeEndpoint.class))
                    .permitAll()
                // ── Everything else is denied ──────────────────────────────
                // /actuator/env     → leaks env vars and secrets
                // /actuator/beans   → leaks full Spring context
                // /actuator/loggers → allows runtime log-level changes
                // /actuator/shutdown → shuts down the service
                // /actuator/mappings, /actuator/threaddump, etc.
                .anyRequest().denyAll()
            )
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
