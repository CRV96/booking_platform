package com.booking.platform.event_service.exception;

import io.grpc.Status;

/**
 * Thrown when input validation fails.
 */
public class ValidationException extends EventServiceException {

    public ValidationException(String message) {
        super(message);
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.INVALID_ARGUMENT;
    }
}
