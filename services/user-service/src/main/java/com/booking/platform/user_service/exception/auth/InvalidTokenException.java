package com.booking.platform.user_service.exception.auth;

/**
 * Thrown when a token (access or refresh) is invalid or expired.
 */
public class InvalidTokenException extends AuthenticationException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
