package com.booking.platform.event_service.exception;

import io.grpc.Status;

/**
 * Thrown when a user attempts an operation they don't have permission for.
 */
public class PermissionDeniedException extends EventServiceException {

    public PermissionDeniedException(String message) {
        super(message);
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.PERMISSION_DENIED;
    }
}
