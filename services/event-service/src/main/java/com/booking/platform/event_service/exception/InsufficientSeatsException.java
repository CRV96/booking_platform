package com.booking.platform.event_service.exception;

import io.grpc.Status;

/**
 * Thrown when there are not enough available seats
 * for the requested seat category.
 */
public class InsufficientSeatsException extends EventServiceException {

    public InsufficientSeatsException(String eventId, String seatCategory, int requested, int available) {
        super(String.format("Insufficient seats for event %s, category '%s': requested %d, available %d",
                eventId, seatCategory, requested, available));
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.FAILED_PRECONDITION;
    }
}
