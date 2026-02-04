package com.booking.platform.user_service.config;

import com.booking.platform.user_service.properties.KeycloakProperties;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KeycloakProperties.class)
public class KeycloakConfig {

    @Bean
    public Keycloak keycloak(KeycloakProperties properties) {
        return KeycloakBuilder.builder()
            .serverUrl(properties.serverUrl())
            .realm(properties.realm())
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .clientId(properties.clientId())
            .clientSecret(properties.clientSecret())
            .build();
    }
}
