package com.booking.platform.user_service.service;

import com.booking.platform.user_service.exception.InvalidCredentialsException;
import com.booking.platform.user_service.exception.InvalidTokenException;

/**
 * Service for handling authentication operations with Keycloak.
 */
public interface AuthService {

    /**
     * Authenticates a user with username/password and returns tokens.
     *
     * @param username The username (or email)
     * @param password The user's password
     * @return Token response from Keycloak
     * @throws InvalidCredentialsException if credentials are invalid
     */
    TokenResponse login(String username, String password);

    /**
     * Exchanges a refresh token for new access and refresh tokens.
     *
     * @param refreshToken The refresh token
     * @return New token response from Keycloak
     * @throws InvalidTokenException if the refresh token is invalid or expired
     */
    TokenResponse refreshToken(String refreshToken);

    /**
     * Invalidates a refresh token (logout).
     *
     * @param refreshToken The refresh token to invalidate
     * @return true if logout was successful
     */
    boolean logout(String refreshToken);

    /**
     * Token response from Keycloak token endpoint.
     */
    record TokenResponse(
        String access_token,
        String refresh_token,
        int expires_in,
        int refresh_expires_in,
        String token_type
    ) {}
}
