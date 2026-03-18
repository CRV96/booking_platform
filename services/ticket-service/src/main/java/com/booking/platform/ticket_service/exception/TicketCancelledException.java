package com.booking.platform.ticket_service.exception;

import com.booking.platform.common.exception.ServiceException;
import io.grpc.Status;

public class TicketCancelledException extends ServiceException {

    public TicketCancelledException(String identifier) {
        super("Ticket is cancelled: " + identifier);
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.FAILED_PRECONDITION;
    }
}
