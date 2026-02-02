package com.booking.platform.user_service.service.impl;

import com.booking.platform.user_service.config.KeycloakAuthProperties;
import com.booking.platform.user_service.exception.AuthenticationException;
import com.booking.platform.user_service.exception.InvalidCredentialsException;
import com.booking.platform.user_service.exception.InvalidTokenException;
import com.booking.platform.user_service.service.KeycloakAuthService;
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
public class KeycloakAuthServiceImpl implements KeycloakAuthService {

    private final WebClient webClient;
    private final KeycloakAuthProperties authProperties;

    @Override
    public KeycloakAuthService.TokenResponse login(String username, String password) {
        log.debug("Attempting login for user: {}", username);

        try {
            KeycloakAuthService.TokenResponse response = webClient.post()
                    .uri(authProperties.getTokenEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters
                            .fromFormData("grant_type", "password")
                            .with("client_id", authProperties.clientId())
                            .with("username", username)
                            .with("password", password))
                    .retrieve()
                    .bodyToMono(KeycloakAuthService.TokenResponse.class)
                    .block();

            log.info("Login successful for user: {}", username);
            return response;

        } catch (WebClientResponseException.Unauthorized | WebClientResponseException.BadRequest e) {
            log.warn("Invalid credentials for user: {}", username);
            throw new InvalidCredentialsException("Invalid username or password");
        } catch (Exception e) {
            log.error("Login failed for user: {}", username, e);
            throw new AuthenticationException("Authentication failed: " + e.getMessage());
        }
    }

    @Override
    public KeycloakAuthService.TokenResponse refreshToken(String refreshToken) {
        log.debug("Attempting to refresh token");

        try {
            KeycloakAuthService.TokenResponse response = webClient.post()
                    .uri(authProperties.getTokenEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters
                            .fromFormData("grant_type", "refresh_token")
                            .with("client_id", authProperties.clientId())
                            .with("refresh_token", refreshToken))
                    .retrieve()
                    .bodyToMono(KeycloakAuthService.TokenResponse.class)
                    .block();

            log.debug("Token refresh successful");
            return response;

        } catch (WebClientResponseException.BadRequest e) {
            log.warn("Invalid or expired refresh token");
            throw new InvalidTokenException("Invalid or expired refresh token");
        } catch (Exception e) {
            log.error("Token refresh failed", e);
            throw new AuthenticationException("Token refresh failed: " + e.getMessage());
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
                            .fromFormData("client_id", authProperties.clientId())
                            .with("refresh_token", refreshToken))
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
