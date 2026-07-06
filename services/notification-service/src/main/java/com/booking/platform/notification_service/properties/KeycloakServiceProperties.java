package com.booking.platform.notification_service.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keycloak.service")
public record KeycloakServiceProperties(String tokenUrl, String clientId, String clientSecret) {
}
