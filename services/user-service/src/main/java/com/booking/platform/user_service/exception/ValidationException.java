package com.booking.platform.user_service.exception;

import io.grpc.Status;

/**
 * Thrown when input validation fails.
 */
public class ValidationException extends UserServiceException {

    public ValidationException(String message) {
        super(message);
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.INVALID_ARGUMENT;
    }
}
