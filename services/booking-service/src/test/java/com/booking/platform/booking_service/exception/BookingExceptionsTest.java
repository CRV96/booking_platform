package com.booking.platform.booking_service.exception;

import com.booking.platform.booking_service.entity.enums.BookingStatus;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookingExceptionsTest {

    private static final String BOOKING_ID = "bk-99";

    // ── BookingNotFoundException ───────────────────────────────────────────────

    @Test
    void bookingNotFound_messageContainsId() {
        BookingNotFoundException ex = new BookingNotFoundException(BOOKING_ID);

        assertThat(ex.getMessage()).contains(BOOKING_ID);
        assertThat(ex.getGrpcStatusCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    // ── BookingAlreadyCancelledException ──────────────────────────────────────

    @Test
    void alreadyCancelled_messageContainsId() {
        BookingAlreadyCancelledException ex = new BookingAlreadyCancelledException(BOOKING_ID);

        assertThat(ex.getMessage()).contains(BOOKING_ID);
        assertThat(ex.getGrpcStatusCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
    }

    // ── DuplicateBookingException ─────────────────────────────────────────────

    @Test
    void duplicateBooking_messageContainsKey() {
        DuplicateBookingException ex = new DuplicateBookingException("key-abc");

        assertThat(ex.getMessage()).contains("key-abc");
        assertThat(ex.getGrpcStatusCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
    }

    // ── EventNotAvailableException ────────────────────────────────────────────

    @Test
    void eventNotAvailable_messageContainsEventIdAndReason() {
        EventNotAvailableException ex = new EventNotAvailableException("ev-1", "sold out");

        assertThat(ex.getMessage()).contains("ev-1");
        assertThat(ex.getMessage()).contains("sold out");
        assertThat(ex.getGrpcStatusCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
    }

    // ── InvalidBookingStateException ──────────────────────────────────────────

    @Test
    void invalidState_messageContainsIdAndStatuses() {
        InvalidBookingStateException ex = new InvalidBookingStateException(
                BOOKING_ID, BookingStatus.CANCELLED, BookingStatus.PENDING);

        assertThat(ex.getMessage()).contains(BOOKING_ID);
        assertThat(ex.getMessage()).contains("CANCELLED");
        assertThat(ex.getMessage()).contains("PENDING");
        assertThat(ex.getGrpcStatusCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
    }

    // ── SeatLockException ─────────────────────────────────────────────────────

    @Test
    void seatLock_messageContainsEventAndCategory() {
        SeatLockException ex = new SeatLockException("ev-1", "VIP");

        assertThat(ex.getMessage()).contains("ev-1");
        assertThat(ex.getMessage()).contains("VIP");
        assertThat(ex.getGrpcStatusCode()).isEqualTo(Status.Code.UNAVAILABLE);
    }

    // ── UnauthorizedBookingAccessException ────────────────────────────────────

    @Test
    void unauthorizedAccess_messageContainsId() {
        UnauthorizedBookingAccessException ex = new UnauthorizedBookingAccessException(BOOKING_ID);

        assertThat(ex.getMessage()).contains(BOOKING_ID);
        assertThat(ex.getGrpcStatusCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    // ── Hierarchy ─────────────────────────────────────────────────────────────

    @Test
    void allExceptions_extendBookingServiceException() {
        assertThat(new BookingNotFoundException("x")).isInstanceOf(BookingServiceException.class);
        assertThat(new BookingAlreadyCancelledException("x")).isInstanceOf(BookingServiceException.class);
        assertThat(new DuplicateBookingException("x")).isInstanceOf(BookingServiceException.class);
        assertThat(new EventNotAvailableException("x", "r")).isInstanceOf(BookingServiceException.class);
        assertThat(new InvalidBookingStateException("x", BookingStatus.PENDING, BookingStatus.CONFIRMED))
                .isInstanceOf(BookingServiceException.class);
        assertThat(new SeatLockException("x", "y")).isInstanceOf(BookingServiceException.class);
        assertThat(new UnauthorizedBookingAccessException("x")).isInstanceOf(BookingServiceException.class);
    }
}
