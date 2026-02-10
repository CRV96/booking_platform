package com.booking.platform.event_service.exception;

import io.grpc.Status;

/**
 * Thrown when an operation is attempted on an event in an invalid state.
 * For example, trying to publish an already cancelled event.
 */
public class InvalidEventStateException extends EventServiceException {

    public InvalidEventStateException(String message) {
        super(message);
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.FAILED_PRECONDITION;
    }
}
