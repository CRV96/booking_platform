package com.booking.platform.booking_service.exception;

import io.grpc.Status;

/**
 * Thrown when a cancellation is requested for a booking that is already cancelled or refunded.
 */
public class BookingAlreadyCancelledException extends BookingServiceException {

    public BookingAlreadyCancelledException(String bookingId) {
        super("Booking is already cancelled or refunded: " + bookingId);
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.FAILED_PRECONDITION;
    }
}
