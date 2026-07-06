package com.booking.platform.payment_service.entity.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the {@link PaymentStatus} state machine.
 * Verifies every documented valid transition and a representative set of invalid ones.
 */
class PaymentStatusTest {

    // ── INITIATED ─────────────────────────────────────────────────────────────

    @Test
    void initiated_canTransitionTo_processing() {
        assertThat(PaymentStatus.INITIATED.canTransitionTo(PaymentStatus.PROCESSING)).isTrue();
    }

    @Test
    void initiated_canTransitionTo_pendingRetry() {
        assertThat(PaymentStatus.INITIATED.canTransitionTo(PaymentStatus.PENDING_RETRY)).isTrue();
    }

    @Test
    void initiated_canTransitionTo_failed() {
        assertThat(PaymentStatus.INITIATED.canTransitionTo(PaymentStatus.FAILED)).isTrue();
    }

    @Test
    void initiated_cannotTransitionTo_completed() {
        assertThat(PaymentStatus.INITIATED.canTransitionTo(PaymentStatus.COMPLETED)).isFalse();
    }

    @Test
    void initiated_cannotTransitionTo_refundInitiated() {
        assertThat(PaymentStatus.INITIATED.canTransitionTo(PaymentStatus.REFUND_INITIATED)).isFalse();
    }

    @Test
    void initiated_cannotTransitionTo_refunded() {
        assertThat(PaymentStatus.INITIATED.canTransitionTo(PaymentStatus.REFUNDED)).isFalse();
    }

    // ── PROCESSING ────────────────────────────────────────────────────────────

    @Test
    void processing_canTransitionTo_completed() {
        assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.COMPLETED)).isTrue();
    }

    @Test
    void processing_canTransitionTo_failed() {
        assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.FAILED)).isTrue();
    }

    @Test
    void processing_canTransitionTo_pendingRetry() {
        assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.PENDING_RETRY)).isTrue();
    }

    @Test
    void processing_canTransitionTo_processing_selfLoop() {
        assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.PROCESSING)).isTrue();
    }

    @Test
    void processing_cannotTransitionTo_initiated() {
        assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.INITIATED)).isFalse();
    }

    @Test
    void processing_cannotTransitionTo_refundInitiated() {
        assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.REFUND_INITIATED)).isFalse();
    }

    // ── PENDING_RETRY ─────────────────────────────────────────────────────────

    @Test
    void pendingRetry_canTransitionTo_processing() {
        assertThat(PaymentStatus.PENDING_RETRY.canTransitionTo(PaymentStatus.PROCESSING)).isTrue();
    }

    @Test
    void pendingRetry_canTransitionTo_failed() {
        assertThat(PaymentStatus.PENDING_RETRY.canTransitionTo(PaymentStatus.FAILED)).isTrue();
    }

    @Test
    void pendingRetry_cannotTransitionTo_completed() {
        assertThat(PaymentStatus.PENDING_RETRY.canTransitionTo(PaymentStatus.COMPLETED)).isFalse();
    }

    @Test
    void pendingRetry_cannotTransitionTo_initiated() {
        assertThat(PaymentStatus.PENDING_RETRY.canTransitionTo(PaymentStatus.INITIATED)).isFalse();
    }

    // ── COMPLETED ─────────────────────────────────────────────────────────────

    @Test
    void completed_canTransitionTo_refundInitiated() {
        assertThat(PaymentStatus.COMPLETED.canTransitionTo(PaymentStatus.REFUND_INITIATED)).isTrue();
    }

    @Test
    void completed_cannotTransitionTo_failed() {
        assertThat(PaymentStatus.COMPLETED.canTransitionTo(PaymentStatus.FAILED)).isFalse();
    }

    @Test
    void completed_cannotTransitionTo_processing() {
        assertThat(PaymentStatus.COMPLETED.canTransitionTo(PaymentStatus.PROCESSING)).isFalse();
    }

    @Test
    void completed_cannotTransitionTo_refunded() {
        assertThat(PaymentStatus.COMPLETED.canTransitionTo(PaymentStatus.REFUNDED)).isFalse();
    }

    // ── FAILED (terminal) ─────────────────────────────────────────────────────

    @Test
    void failed_cannotTransitionTo_anyState() {
        for (PaymentStatus next : PaymentStatus.values()) {
            assertThat(PaymentStatus.FAILED.canTransitionTo(next))
                    .as("FAILED -> %s should be blocked", next)
                    .isFalse();
        }
    }

    // ── REFUND_INITIATED ──────────────────────────────────────────────────────

    @Test
    void refundInitiated_canTransitionTo_refunded() {
        assertThat(PaymentStatus.REFUND_INITIATED.canTransitionTo(PaymentStatus.REFUNDED)).isTrue();
    }

    @Test
    void refundInitiated_cannotTransitionTo_completed() {
        assertThat(PaymentStatus.REFUND_INITIATED.canTransitionTo(PaymentStatus.COMPLETED)).isFalse();
    }

    @Test
    void refundInitiated_cannotTransitionTo_failed() {
        assertThat(PaymentStatus.REFUND_INITIATED.canTransitionTo(PaymentStatus.FAILED)).isFalse();
    }

    // ── REFUNDED (terminal) ───────────────────────────────────────────────────

    @Test
    void refunded_cannotTransitionTo_anyState() {
        for (PaymentStatus next : PaymentStatus.values()) {
            assertThat(PaymentStatus.REFUNDED.canTransitionTo(next))
                    .as("REFUNDED -> %s should be blocked", next)
                    .isFalse();
        }
    }
}
