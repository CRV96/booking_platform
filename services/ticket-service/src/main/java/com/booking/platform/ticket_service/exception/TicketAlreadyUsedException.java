package com.booking.platform.ticket_service.exception;

import com.booking.platform.common.exception.ServiceException;
import io.grpc.Status;

/**
 * Exception thrown when an attempt is made to use a ticket that has already been marked as used.
 */
public class TicketAlreadyUsedException extends ServiceException {

    public TicketAlreadyUsedException(String identifier) {
        super("Ticket already used: " + identifier);
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.FAILED_PRECONDITION;
    }
}
