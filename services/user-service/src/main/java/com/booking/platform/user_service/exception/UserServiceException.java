package com.booking.platform.user_service.exception;

import io.grpc.Status;

/**
 * Base exception for all user service exceptions.
 * Provides mapping to gRPC status codes.
 */
public abstract class UserServiceException extends RuntimeException {

    protected UserServiceException(String message) {
        super(message);
    }

    protected UserServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Returns the gRPC status code for this exception.
     */
    public abstract Status.Code getGrpcStatusCode();
}
