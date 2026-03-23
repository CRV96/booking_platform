package com.booking.platform.booking_service.exception;

import com.booking.platform.booking_service.entity.enums.BookingStatus;
import io.grpc.Status;

/**
 * Thrown when a booking operation is attempted on a booking whose current status
 * does not allow the requested transition.
 */
public class InvalidBookingStateException extends BookingServiceException {

    public InvalidBookingStateException(String bookingId, BookingStatus currentStatus, BookingStatus expectedStatus) {
        super("Booking '" + bookingId + "' is in status " + currentStatus
                + ", expected " + expectedStatus);
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.FAILED_PRECONDITION;
    }
}
