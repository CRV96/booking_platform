package com.booking.platform.ticket_service.exception;

import com.booking.platform.common.exception.ServiceException;
import io.grpc.Status;

/**
 * Exception thrown when an invalid operation is attempted on a ticket,
 * such as using an already used ticket or cancelling a ticket that has already been cancelled.
 */
public class InvalidTicketOperationException extends ServiceException {

    public InvalidTicketOperationException(String message) {
        super(message);
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.INVALID_ARGUMENT;
    }
}
