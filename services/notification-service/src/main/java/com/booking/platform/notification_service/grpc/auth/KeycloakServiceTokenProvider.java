package com.booking.platform.notification_service.grpc.auth;

import com.booking.platform.notification_service.properties.KeycloakServiceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.booking.platform.common.logging.ApplicationLogger;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;

@Component
@Slf4j
public class KeycloakServiceTokenProvider {

    private static final int EXPIRY_BUFFER_SECONDS = 30;

    private final RestClient restClient;
    private final KeycloakServiceProperties properties;
    private final ObjectMapper objectMapper;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.MIN;

    public KeycloakServiceTokenProvider(RestClient keycloakRestClient,
                                        KeycloakServiceProperties properties,
                                        ObjectMapper objectMapper) {
        this.restClient = keycloakRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public synchronized String getToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }
        return refreshToken();
    }

    private String refreshToken() {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Requesting new service account token from Keycloak");

        String formBody = "grant_type=client_credentials"
                + "&client_id=" + properties.clientId()
                + "&client_secret=" + properties.clientSecret();

        String responseBody = restClient.post()
                .uri(properties.tokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formBody)
                .retrieve()
                .body(String.class);

        try {
            JsonNode json = objectMapper.readTree(responseBody);
            cachedToken = json.get("access_token").asText();
            int expiresIn = json.get("expires_in").asInt();
            tokenExpiry = Instant.now().plusSeconds(expiresIn - EXPIRY_BUFFER_SECONDS);
            ApplicationLogger.logMessage(log, Level.DEBUG, "Obtained service account token, expires in {}s", expiresIn);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Keycloak token response", e);
        }

        return cachedToken;
    }
}
