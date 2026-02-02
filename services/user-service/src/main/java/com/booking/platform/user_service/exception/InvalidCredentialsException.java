package com.booking.platform.user_service.exception;

/**
 * Thrown when login credentials are invalid.
 */
public class InvalidCredentialsException extends AuthenticationException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
