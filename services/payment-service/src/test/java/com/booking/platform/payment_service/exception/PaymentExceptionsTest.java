package com.booking.platform.payment_service.exception;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentExceptionsTest {

    // ── PaymentGatewayException ───────────────────────────────────────────────

    @Test
    void paymentGatewayException_messageAndCausePreserved() {
        RuntimeException cause = new RuntimeException("stripe SDK error");
        PaymentGatewayException ex = new PaymentGatewayException("Card declined", cause);

        assertThat(ex.getMessage()).isEqualTo("Card declined");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void paymentGatewayException_nullCause_allowed() {
        PaymentGatewayException ex = new PaymentGatewayException("Some error", null);
        assertThat(ex.getMessage()).isEqualTo("Some error");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void paymentGatewayException_extendsRuntimeException() {
        assertThat(new PaymentGatewayException("msg", null)).isInstanceOf(RuntimeException.class);
    }

    // ── PaymentGatewayUnavailableException ────────────────────────────────────

    @Test
    void paymentGatewayUnavailableException_messageOnly_preserved() {
        PaymentGatewayUnavailableException ex = new PaymentGatewayUnavailableException("Circuit breaker open");
        assertThat(ex.getMessage()).isEqualTo("Circuit breaker open");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void paymentGatewayUnavailableException_withCause_preserved() {
        RuntimeException cause = new RuntimeException("timeout");
        PaymentGatewayUnavailableException ex = new PaymentGatewayUnavailableException("Timeout", cause);
        assertThat(ex.getMessage()).isEqualTo("Timeout");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void paymentGatewayUnavailableException_extendsRuntimeException() {
        assertThat(new PaymentGatewayUnavailableException("msg")).isInstanceOf(RuntimeException.class);
    }

    // ── PaymentNotFoundException ──────────────────────────────────────────────

    @Test
    void paymentNotFoundException_messageContainsId() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        PaymentNotFoundException ex = new PaymentNotFoundException(id);
        assertThat(ex.getMessage()).contains(id.toString());
    }

    @Test
    void paymentNotFoundException_extendsRuntimeException() {
        assertThat(new PaymentNotFoundException(UUID.randomUUID())).isInstanceOf(RuntimeException.class);
    }
}
