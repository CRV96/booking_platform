package com.booking.platform.booking_service.exception;

import io.grpc.Status;

/**
 * Thrown when a user attempts to access or modify a booking that belongs to another user.
 */
public class UnauthorizedBookingAccessException extends BookingServiceException {

    public UnauthorizedBookingAccessException(String bookingId) {
        super("Access denied to booking: " + bookingId);
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.PERMISSION_DENIED;
    }
}
