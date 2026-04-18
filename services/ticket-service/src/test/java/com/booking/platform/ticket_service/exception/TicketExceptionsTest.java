package com.booking.platform.ticket_service.exception;

import com.booking.platform.common.exception.ServiceException;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicketExceptionsTest {

    // ── TicketNotFoundException ───────────────────────────────────────────────

    @Test
    void ticketNotFound_messageContainsIdentifier() {
        assertThat(new TicketNotFoundException("TKT-20240101-ABCDEF").getMessage())
                .contains("TKT-20240101-ABCDEF");
    }

    @Test
    void ticketNotFound_grpcStatusIsNotFound() {
        assertThat(new TicketNotFoundException("x").getGrpcStatusCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    // ── TicketAlreadyUsedException ────────────────────────────────────────────

    @Test
    void ticketAlreadyUsed_messageContainsIdentifier() {
        assertThat(new TicketAlreadyUsedException("TKT-20240101-ABCDEF").getMessage())
                .contains("TKT-20240101-ABCDEF");
    }

    @Test
    void ticketAlreadyUsed_grpcStatusIsFailedPrecondition() {
        assertThat(new TicketAlreadyUsedException("x").getGrpcStatusCode())
                .isEqualTo(Status.Code.FAILED_PRECONDITION);
    }

    // ── TicketCancelledException ──────────────────────────────────────────────

    @Test
    void ticketCancelled_messageContainsIdentifier() {
        assertThat(new TicketCancelledException("TKT-20240101-ABCDEF").getMessage())
                .contains("TKT-20240101-ABCDEF");
    }

    @Test
    void ticketCancelled_grpcStatusIsFailedPrecondition() {
        assertThat(new TicketCancelledException("x").getGrpcStatusCode())
                .isEqualTo(Status.Code.FAILED_PRECONDITION);
    }

    // ── InvalidTicketOperationException ──────────────────────────────────────

    @Test
    void invalidTicketOperation_messagePreserved() {
        assertThat(new InvalidTicketOperationException("bookingId is required").getMessage())
                .isEqualTo("bookingId is required");
    }

    @Test
    void invalidTicketOperation_grpcStatusIsInvalidArgument() {
        assertThat(new InvalidTicketOperationException("msg").getGrpcStatusCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    // ── Common contract ───────────────────────────────────────────────────────

    @Test
    void allExceptions_extendServiceException() {
        assertThat(new TicketNotFoundException("x")).isInstanceOf(ServiceException.class);
        assertThat(new TicketAlreadyUsedException("x")).isInstanceOf(ServiceException.class);
        assertThat(new TicketCancelledException("x")).isInstanceOf(ServiceException.class);
        assertThat(new InvalidTicketOperationException("x")).isInstanceOf(ServiceException.class);
    }
}
