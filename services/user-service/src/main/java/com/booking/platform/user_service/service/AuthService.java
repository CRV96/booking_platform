package com.booking.platform.user_service.service;

import com.booking.platform.user_service.exception.auth.InvalidCredentialsException;
import com.booking.platform.user_service.exception.auth.InvalidTokenException;

/**
 * Service for handling authentication operations with Keycloak.
 */
public interface AuthService<RESPONSE> {

    /**
     * Authenticates a user with username/password and returns tokens.
     *
     * @param username The username (or email)
     * @param password The user's password
     * @return Token response
     * @throws InvalidCredentialsException if credentials are invalid
     */
    RESPONSE login(String username, String password);

    /**
     * Exchanges a refresh token for new access and refresh tokens.
     *
     * @param refreshToken The refresh token
     * @return New token response
     * @throws InvalidTokenException if the refresh token is invalid or expired
     */
    RESPONSE refreshToken(String refreshToken);

    /**
     * Invalidates a refresh token (logout).
     *
     * @param refreshToken The refresh token to invalidate
     * @return true if logout was successful
     */
    boolean logout(String refreshToken);

}
