package com.booking.platform.user_service.exception.user;

import com.booking.platform.user_service.exception.UserServiceException;
import io.grpc.Status;

/**
 * Thrown when attempting to create a user that already exists.
 */
public class UserAlreadyExistsException extends UserServiceException {

    public UserAlreadyExistsException(String message) {
        super(message);
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.ALREADY_EXISTS;
    }
}
