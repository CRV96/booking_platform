package com.booking.platform.ticket_service.exception;

import com.booking.platform.common.exception.ServiceException;
import io.grpc.Status;

public class TicketAlreadyUsedException extends ServiceException {

    public TicketAlreadyUsedException(String identifier) {
        super("Ticket already used: " + identifier);
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.FAILED_PRECONDITION;
    }
}
