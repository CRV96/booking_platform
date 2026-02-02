package com.booking.platform.user_service.exception;

/**
 * Thrown when attempting to create a user that already exists.
 */
public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
