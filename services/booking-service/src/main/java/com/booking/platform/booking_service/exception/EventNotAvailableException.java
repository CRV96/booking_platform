package com.booking.platform.booking_service.exception;

import io.grpc.Status;

/**
 * Thrown when a booking cannot be created because the event is not in a bookable state
 * (e.g. cancelled, sold out, or the requested seat category has no available seats).
 */
public class EventNotAvailableException extends BookingServiceException {

    public EventNotAvailableException(String eventId, String reason) {
        super("Event not available for booking [eventId=" + eventId + "]: " + reason);
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.FAILED_PRECONDITION;
    }
}
