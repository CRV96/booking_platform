package com.booking.platform.ticket_service.exception;

import com.booking.platform.common.exception.ServiceException;
import io.grpc.Status;

public class InvalidTicketOperationException extends ServiceException {

    public InvalidTicketOperationException(String message) {
        super(message);
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.INVALID_ARGUMENT;
    }
}
