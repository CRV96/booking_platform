package com.booking.platform.user_service.service.impl;

import com.booking.platform.common.enums.Keycloak;
import com.booking.platform.user_service.dto.TokenResponseDTO;
import com.booking.platform.user_service.properties.KeycloakAuthProperties;
import com.booking.platform.user_service.exception.auth.AuthenticationException;
import com.booking.platform.user_service.exception.auth.InvalidCredentialsException;
import com.booking.platform.user_service.exception.auth.InvalidTokenException;
import com.booking.platform.user_service.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Service for handling authentication operations with Keycloak.
 * Uses the public client (booking-app) to authenticate users and manage tokens.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties(KeycloakAuthProperties.class)
public class KeycloakAuthServiceImpl implements AuthService {

    // Form field names (Keycloak OAuth2 token endpoint)
    private static final String FORM_GRANT_TYPE = "grant_type";
    private static final String FORM_CLIENT_ID = "client_id";
    private static final String FORM_USERNAME = "username";
    private static final String FORM_PASSWORD = "password";
    private static final String FORM_REFRESH_TOKEN = "refresh_token";

    // Error messages
    private static final String ERROR_INVALID_CREDENTIALS = "Invalid username or password";
    private static final String ERROR_INVALID_REFRESH_TOKEN = "Invalid or expired refresh token";
    private static final String ERROR_AUTH_FAILED = "Authentication failed: %s";
    private static final String ERROR_REFRESH_FAILED = "Token refresh failed: %s";

    private final WebClient webClient;
    private final KeycloakAuthProperties authProperties;

    @Override
    public TokenResponseDTO login(String username, String password) {
        log.debug("Attempting login for user: {}", username);

        try {
            TokenResponseDTO response = webClient.post()
                    .uri(authProperties.getTokenEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters
                            .fromFormData(FORM_GRANT_TYPE, Keycloak.GRANT_TYPE_PASSWORD.getValue())
                            .with(FORM_CLIENT_ID, authProperties.clientId())
                            .with(FORM_USERNAME, username)
                            .with(FORM_PASSWORD, password))
                    .retrieve()
                    .bodyToMono(TokenResponseDTO.class)
                    .block();

            log.info("Login successful for user: {}", username);
            return response;

        } catch (WebClientResponseException.Unauthorized | WebClientResponseException.BadRequest e) {
            log.warn("Invalid credentials for user: {}", username);
            throw new InvalidCredentialsException(ERROR_INVALID_CREDENTIALS);
        } catch (Exception e) {
            log.error("Login failed for user: {}", username, e);
            throw new AuthenticationException(String.format(ERROR_AUTH_FAILED, e.getMessage()));
        }
    }

    @Override
    public TokenResponseDTO refreshToken(String refreshToken) {
        log.debug("Attempting to refresh token");

        try {
            TokenResponseDTO response = webClient.post()
                    .uri(authProperties.getTokenEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters
                            .fromFormData(FORM_GRANT_TYPE, Keycloak.GRANT_TYPE_REFRESH_TOKEN.getValue())
                            .with(FORM_CLIENT_ID, authProperties.clientId())
                            .with(FORM_REFRESH_TOKEN, refreshToken))
                    .retrieve()
                    .bodyToMono(TokenResponseDTO.class)
                    .block();

            log.debug("Token refresh successful");
            return response;

        } catch (WebClientResponseException.BadRequest e) {
            log.warn("Invalid or expired refresh token");
            throw new InvalidTokenException(ERROR_INVALID_REFRESH_TOKEN);
        } catch (Exception e) {
            log.error("Token refresh failed", e);
            throw new AuthenticationException(String.format(ERROR_REFRESH_FAILED, e.getMessage()));
        }
    }

    @Override
    public boolean logout(String refreshToken) {
        log.debug("Attempting logout");

        try {
            webClient.post()
                    .uri(authProperties.getLogoutEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters
                            .fromFormData(FORM_CLIENT_ID, authProperties.clientId())
                            .with(FORM_REFRESH_TOKEN, refreshToken))
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("Logout successful");
            return true;

        } catch (Exception e) {
            log.warn("Logout failed (token may already be invalid)", e);
            return false;
        }
    }

}
