package com.booking.platform.event_service.exception;

import io.grpc.Status;

/**
 * Thrown when an event cannot be found by its ID.
 */
public class EventNotFoundException extends EventServiceException {

    public EventNotFoundException(String eventId) {
        super("Event not found: " + eventId);
    }

    public EventNotFoundException(String eventId, String operation) {
        super("Event not found: " + eventId + " (during " + operation + ")");
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.NOT_FOUND;
    }
}
