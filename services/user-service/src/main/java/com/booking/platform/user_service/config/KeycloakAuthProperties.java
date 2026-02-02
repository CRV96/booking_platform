package com.booking.platform.user_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Keycloak authentication operations.
 * Uses the public client (booking-app) for user login/token operations.
 */
@ConfigurationProperties(prefix = "keycloak.auth")
public record KeycloakAuthProperties(
    String serverUrl,
    String realm,
    String clientId
) {
    /**
     * Returns the token endpoint URL.
     */
    public String getTokenEndpoint() {
        return serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    /**
     * Returns the logout endpoint URL.
     */
    public String getLogoutEndpoint() {
        return serverUrl + "/realms/" + realm + "/protocol/openid-connect/logout";
    }
}
