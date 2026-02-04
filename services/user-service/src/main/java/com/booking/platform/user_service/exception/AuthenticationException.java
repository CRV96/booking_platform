package com.booking.platform.user_service.exception;

import io.grpc.Status;

/**
 * Base exception for authentication-related errors.
 */
public class AuthenticationException extends UserServiceException {

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.UNAUTHENTICATED;
    }
}
