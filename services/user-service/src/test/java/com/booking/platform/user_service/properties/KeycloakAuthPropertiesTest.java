package com.booking.platform.user_service.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakAuthPropertiesTest {

    private final KeycloakAuthProperties props =
            new KeycloakAuthProperties("http://keycloak:8080", "booking-platform", "booking-app");

    @Test
    void getTokenEndpoint_buildsCorrectUrl() {
        assertThat(props.getTokenEndpoint())
                .isEqualTo("http://keycloak:8080/realms/booking-platform/protocol/openid-connect/token");
    }

    @Test
    void getLogoutEndpoint_buildsCorrectUrl() {
        assertThat(props.getLogoutEndpoint())
                .isEqualTo("http://keycloak:8080/realms/booking-platform/protocol/openid-connect/logout");
    }

    @Test
    void getTokenEndpoint_containsServerUrlAndRealm() {
        KeycloakAuthProperties custom =
                new KeycloakAuthProperties("https://auth.example.com", "my-realm", "client");
        assertThat(custom.getTokenEndpoint())
                .startsWith("https://auth.example.com")
                .contains("my-realm");
    }

    @Test
    void getLogoutEndpoint_containsServerUrlAndRealm() {
        KeycloakAuthProperties custom =
                new KeycloakAuthProperties("https://auth.example.com", "my-realm", "client");
        assertThat(custom.getLogoutEndpoint())
                .startsWith("https://auth.example.com")
                .contains("my-realm");
    }
}
