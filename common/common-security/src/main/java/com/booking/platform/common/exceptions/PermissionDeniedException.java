package com.booking.platform.common.exceptions;

import com.booking.platform.common.exception.ServiceException;
import io.grpc.Status;

/**
 * Thrown when a user attempts an operation they don't have permission for.
 */
public class PermissionDeniedException extends ServiceException {

    public PermissionDeniedException(String message) {
        super(message);
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.PERMISSION_DENIED;
    }
}
