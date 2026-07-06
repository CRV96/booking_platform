package com.booking.platform.event_service.exception;

import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventExceptionsTest {

    // ── EventNotFoundException ────────────────────────────────────────────────

    @Test
    void eventNotFound_singleArg_containsEventId() {
        EventNotFoundException ex = new EventNotFoundException("ev-99");

        assertThat(ex.getMessage()).contains("ev-99");
        assertThat(ex.getGrpcStatusCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void eventNotFound_twoArg_containsEventIdAndOperation() {
        EventNotFoundException ex = new EventNotFoundException("ev-1", "publish");

        assertThat(ex.getMessage()).contains("ev-1");
        assertThat(ex.getMessage()).contains("publish");
        assertThat(ex.getGrpcStatusCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    // ── InvalidEventStateException ────────────────────────────────────────────

    @Test
    void invalidEventState_messagePreserved() {
        InvalidEventStateException ex = new InvalidEventStateException("Cannot publish CANCELLED event");

        assertThat(ex.getMessage()).isEqualTo("Cannot publish CANCELLED event");
        assertThat(ex.getGrpcStatusCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
    }

    // ── InsufficientSeatsException ────────────────────────────────────────────

    @Test
    void insufficientSeats_messageContainsDetails() {
        InsufficientSeatsException ex = new InsufficientSeatsException("ev-1", "VIP", 10, 3);

        assertThat(ex.getMessage()).contains("ev-1");
        assertThat(ex.getMessage()).contains("VIP");
        assertThat(ex.getMessage()).contains("10");
        assertThat(ex.getMessage()).contains("3");
        assertThat(ex.getGrpcStatusCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
    }

    // ── ValidationException ───────────────────────────────────────────────────

    @Test
    void validationException_messagePreserved() {
        ValidationException ex = new ValidationException("Title must not be blank");

        assertThat(ex.getMessage()).isEqualTo("Title must not be blank");
        assertThat(ex.getGrpcStatusCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    // ── PermissionDeniedException ─────────────────────────────────────────────

    @Test
    void permissionDenied_messagePreserved() {
        PermissionDeniedException ex = new PermissionDeniedException("Role 'employee' required");

        assertThat(ex.getMessage()).isEqualTo("Role 'employee' required");
        assertThat(ex.getGrpcStatusCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    // ── Hierarchy ─────────────────────────────────────────────────────────────

    @Test
    void allExceptions_extendEventServiceException() {
        assertThat(new EventNotFoundException("x")).isInstanceOf(EventServiceException.class);
        assertThat(new InvalidEventStateException("x")).isInstanceOf(EventServiceException.class);
        assertThat(new InsufficientSeatsException("x", "y", 1, 0)).isInstanceOf(EventServiceException.class);
        assertThat(new ValidationException("x")).isInstanceOf(EventServiceException.class);
        assertThat(new PermissionDeniedException("x")).isInstanceOf(EventServiceException.class);
    }
}
