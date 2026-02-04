package com.booking.platform.user_service.exception.auth;

/**
 * Thrown when login credentials are invalid.
 */
public class InvalidCredentialsException extends AuthenticationException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
