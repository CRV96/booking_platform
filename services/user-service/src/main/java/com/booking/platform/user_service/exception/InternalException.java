package com.booking.platform.user_service.exception;

import io.grpc.Status;

/**
 * Thrown for unexpected internal errors (e.g., Keycloak API failures).
 */
public class InternalException extends UserServiceException {

    public InternalException(String message) {
        super(message);
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.INTERNAL;
    }
}
