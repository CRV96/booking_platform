package com.booking.platform.booking_service.exception;

import io.grpc.Status;

/**
 * Thrown when a booking cannot be found by its ID.
 */
public class BookingNotFoundException extends BookingServiceException {

    public BookingNotFoundException(String bookingId) {
        super("Booking not found: " + bookingId);
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.NOT_FOUND;
    }
}
