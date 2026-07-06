package com.booking.platform.booking_service.exception;

import io.grpc.Status;

/**
 * Thrown when the distributed lock for a seat category cannot be acquired,
 * indicating high contention. Maps to gRPC UNAVAILABLE so clients can retry.
 */
public class SeatLockException extends BookingServiceException {

    public SeatLockException(String eventId, String seatCategory) {
        super("Unable to acquire seat lock for event " + eventId
                + ", category '" + seatCategory + "'. Try again shortly.");
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.UNAVAILABLE;
    }
}
