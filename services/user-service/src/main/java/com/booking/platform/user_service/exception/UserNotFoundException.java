package com.booking.platform.user_service.exception;

import io.grpc.Status;

/**
 * Thrown when a requested user is not found.
 */
public class UserNotFoundException extends UserServiceException {

    public UserNotFoundException(String message) {
        super(message);
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.NOT_FOUND;
    }
}
