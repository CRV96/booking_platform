package com.booking.platform.booking_service.exception;

import io.grpc.Status;

/**
 * Thrown when a CreateBooking request arrives with an idempotency key that already
 * maps to an existing booking — and the existing booking is in an incompatible state
 * to be returned as an idempotent response.
 *
 * <p>Under normal idempotency handling the existing booking is returned silently.
 * This exception is only raised when the duplicate represents an unambiguous error
 * (e.g. a key collision from a different event/user combination).
 */
public class DuplicateBookingException extends BookingServiceException {

    public DuplicateBookingException(String idempotencyKey) {
        super("A booking with idempotency key already exists: " + idempotencyKey);
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.ALREADY_EXISTS;
    }
}
