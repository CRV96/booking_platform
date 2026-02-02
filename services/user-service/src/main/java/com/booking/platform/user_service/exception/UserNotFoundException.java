package com.booking.platform.user_service.exception;

/**
 * Thrown when a requested user is not found.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
