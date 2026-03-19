package com.booking.platform.ticket_service.exception;

import com.booking.platform.common.exception.ServiceException;
import io.grpc.Status;

/**
 * Exception thrown when a ticket with the specified identifier is not found in the system.
 */
public class TicketNotFoundException extends ServiceException {

    public TicketNotFoundException(String identifier) {
        super("Ticket not found: " + identifier);
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.NOT_FOUND;
    }
}
