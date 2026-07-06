package com.booking.platform.payment_service.service;

import com.booking.platform.payment_service.base.BaseIntegrationTest;
import com.booking.platform.payment_service.dto.GatewayPaymentResponse;
import com.booking.platform.payment_service.dto.GatewayRefundResponse;
import com.booking.platform.payment_service.entity.PaymentEntity;
import com.booking.platform.payment_service.entity.enums.PaymentStatus;
import com.booking.platform.payment_service.exception.PaymentGatewayException;
import com.booking.platform.payment_service.exception.PaymentGatewayUnavailableException;
import com.booking.platform.payment_service.gateway.PaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for {@link com.booking.platform.payment_service.service.impl.PaymentServiceImpl}.
 *
 * <p>Uses real PostgreSQL and Kafka via Testcontainers.
 * The payment gateway is mocked to avoid Stripe/mock delays and to control responses.
 */
class PaymentServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @MockitoBean
    private PaymentGateway paymentGateway;

    private static final String BOOKING_ID = "booking-" + UUID.randomUUID();
    private static final String USER_ID = "user-123";
    private static final BigDecimal AMOUNT = new BigDecimal("99.99");
    private static final String CURRENCY = "USD";

    @BeforeEach
    void setupGatewayMocks() {
        // Default: gateway succeeds for both create and confirm
        when(paymentGateway.createPaymentIntent(any(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(
                        new GatewayPaymentResponse("pi_test_123", "requires_confirmation", "card")));

        when(paymentGateway.confirmPayment(anyString()))
                .thenReturn(CompletableFuture.completedFuture(
                        new GatewayPaymentResponse("pi_test_123", "succeeded", "card")));

        when(paymentGateway.createRefund(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new GatewayRefundResponse("re_test_456", "succeeded")));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // processPayment
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    class ProcessPayment {

        @Test
        void success_returnsCompletedPayment() {
            PaymentEntity payment = paymentService.processPayment(BOOKING_ID, USER_ID, AMOUNT, CURRENCY);

            assertThat(payment.getId()).isNotNull();
            assertThat(payment.getBookingId()).isEqualTo(BOOKING_ID);
            assertThat(payment.getUserId()).isEqualTo(USER_ID);
            assertThat(payment.getAmount()).isEqualByComparingTo(AMOUNT);
            assertThat(payment.getCurrency()).isEqualTo(CURRENCY);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(payment.getExternalPaymentId()).isEqualTo("pi_test_123");
            assertThat(payment.getPaymentMethod()).isEqualTo("card");
            assertThat(payment.getFailureReason()).isNull();

            verify(paymentGateway).createPaymentIntent(AMOUNT, CURRENCY, BOOKING_ID);
            verify(paymentGateway).confirmPayment("pi_test_123");
        }

        @Test
        void success_createsOutboxEvent() {
            PaymentEntity payment = paymentService.processPayment(BOOKING_ID, USER_ID, AMOUNT, CURRENCY);

            List<Map<String, Object>> outboxRows = jdbcTemplate.queryForList(
                    "SELECT * FROM outbox_events WHERE aggregate_id = ?", payment.getId().toString());

            assertThat(outboxRows).hasSize(1);
            assertThat(outboxRows.get(0).get("event_type")).isEqualTo("PaymentCompleted");
            assertThat(outboxRows.get(0).get("aggregate_type")).isEqualTo("Payment");
            assertThat(outboxRows.get(0).get("published_at")).isNull();

            String payload = outboxRows.get(0).get("payload").toString();
            assertThat(payload).contains(BOOKING_ID);
            assertThat(payload).contains(payment.getId().toString());
        }

        @Test
        void idempotency_sameBookingId_returnsSamePayment() {
            PaymentEntity first = paymentService.processPayment(BOOKING_ID, USER_ID, AMOUNT, CURRENCY);
            PaymentEntity second = paymentService.processPayment(BOOKING_ID, USER_ID, AMOUNT, CURRENCY);

            assertThat(second.getId()).isEqualTo(first.getId());

            // Gateway should only be called once
            verify(paymentGateway, times(1)).createPaymentIntent(any(), anyString(), anyString());
        }

        @Test
        void gatewayDeclined_marksPaymentFailed() {
            when(paymentGateway.confirmPayment(anyString()))
                    .thenReturn(CompletableFuture.completedFuture(
                            new GatewayPaymentResponse("pi_test_123", "failed", "card")));

            String bookingId = "booking-declined-" + UUID.randomUUID();
            PaymentEntity payment = paymentService.processPayment(bookingId, USER_ID, AMOUNT, CURRENCY);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailureReason()).contains("Gateway returned status: failed");
        }

        @Test
        void gatewayDeclined_createsFailedOutboxEvent() {
            when(paymentGateway.confirmPayment(anyString()))
                    .thenReturn(CompletableFuture.completedFuture(
                            new GatewayPaymentResponse("pi_test_123", "failed", "card")));

            String bookingId = "booking-declined-outbox-" + UUID.randomUUID();
            PaymentEntity payment = paymentService.processPayment(bookingId, USER_ID, AMOUNT, CURRENCY);

            List<Map<String, Object>> outboxRows = jdbcTemplate.queryForList(
                    "SELECT * FROM outbox_events WHERE aggregate_id = ?", payment.getId().toString());

            assertThat(outboxRows).hasSize(1);
            assertThat(outboxRows.get(0).get("event_type")).isEqualTo("PaymentFailed");

            String payload = outboxRows.get(0).get("payload").toString();
            assertThat(payload).contains("\"reason\":");
        }

        @Test
        void gatewayBusinessError_marksPaymentFailed() {
            when(paymentGateway.createPaymentIntent(any(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.failedFuture(
                            new PaymentGatewayException("Card declined", null)));

            String bookingId = "booking-error-" + UUID.randomUUID();
            PaymentEntity payment = paymentService.processPayment(bookingId, USER_ID, AMOUNT, CURRENCY);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailureReason()).isEqualTo("Card declined");
        }

        @Test
        void gatewayUnavailable_marksPaymentPendingRetry() {
            when(paymentGateway.createPaymentIntent(any(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.failedFuture(
                            new PaymentGatewayUnavailableException("Circuit breaker open")));

            String bookingId = "booking-retry-" + UUID.randomUUID();
            PaymentEntity payment = paymentService.processPayment(bookingId, USER_ID, AMOUNT, CURRENCY);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING_RETRY);
            assertThat(payment.getFailureReason()).isEqualTo("Circuit breaker open");
        }

        @Test
        void gatewayUnavailable_noOutboxEventCreated() {
            when(paymentGateway.createPaymentIntent(any(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.failedFuture(
                            new PaymentGatewayUnavailableException("Timeout")));

            String bookingId = "booking-retry-no-outbox-" + UUID.randomUUID();
            PaymentEntity payment = paymentService.processPayment(bookingId, USER_ID, AMOUNT, CURRENCY);

            List<Map<String, Object>> outboxRows = jdbcTemplate.queryForList(
                    "SELECT * FROM outbox_events WHERE aggregate_id = ?", payment.getId().toString());

            // No outbox event for PENDING_RETRY — nothing to publish yet
            assertThat(outboxRows).isEmpty();
        }

        @Test
        void gatewayUnavailableOnConfirm_marksPaymentPendingRetry() {
            when(paymentGateway.confirmPayment(anyString()))
                    .thenReturn(CompletableFuture.failedFuture(
                            new PaymentGatewayUnavailableException("Gateway timeout on confirm")));

            String bookingId = "booking-confirm-retry-" + UUID.randomUUID();
            PaymentEntity payment = paymentService.processPayment(bookingId, USER_ID, AMOUNT, CURRENCY);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING_RETRY);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // processRefund
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    class ProcessRefund {

        private PaymentEntity completedPayment;

        @BeforeEach
        void createCompletedPayment() {
            String bookingId = "booking-refund-" + UUID.randomUUID();
            completedPayment = paymentService.processPayment(bookingId, USER_ID, AMOUNT, CURRENCY);
            assertThat(completedPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

            // Clear outbox from payment creation so we only see refund events
            jdbcTemplate.execute("DELETE FROM outbox_events");
        }

        @Test
        void success_marksPaymentRefunded() {
            paymentService.processRefund(completedPayment.getBookingId());

            PaymentEntity refunded = findPaymentById(completedPayment.getId());
            assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);

            verify(paymentGateway).createRefund(completedPayment.getExternalPaymentId(), AMOUNT);
        }

        @Test
        void success_createsRefundCompletedOutboxEvent() {
            paymentService.processRefund(completedPayment.getBookingId());

            List<Map<String, Object>> outboxRows = jdbcTemplate.queryForList(
                    "SELECT * FROM outbox_events WHERE aggregate_id = ?",
                    completedPayment.getId().toString());

            assertThat(outboxRows).hasSize(1);
            assertThat(outboxRows.get(0).get("event_type")).isEqualTo("RefundCompleted");
            assertThat(outboxRows.get(0).get("aggregate_type")).isEqualTo("Payment");

            String payload = outboxRows.get(0).get("payload").toString();
            assertThat(payload).contains("re_test_456");
            assertThat(payload).contains(completedPayment.getBookingId());
        }

        @Test
        void noPaymentFound_doesNothing() {
            paymentService.processRefund("nonexistent-booking-id");

            // No gateway call, no outbox event
            verify(paymentGateway, never()).createRefund(anyString(), any());

            List<Map<String, Object>> outboxRows = jdbcTemplate.queryForList(
                    "SELECT * FROM outbox_events");
            assertThat(outboxRows).isEmpty();
        }

        @Test
        void paymentNotCompleted_skipsRefund() {
            // Create a payment that fails (not COMPLETED)
            when(paymentGateway.confirmPayment(anyString()))
                    .thenReturn(CompletableFuture.completedFuture(
                            new GatewayPaymentResponse("pi_skip_test", "failed", "card")));

            String bookingId = "booking-not-completed-" + UUID.randomUUID();
            PaymentEntity failedPayment = paymentService.processPayment(bookingId, USER_ID, AMOUNT, CURRENCY);
            assertThat(failedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);

            // Reset mock for refund calls
            reset(paymentGateway);

            paymentService.processRefund(bookingId);

            // Gateway refund should NOT be called for a FAILED payment
            verify(paymentGateway, never()).createRefund(anyString(), any());
        }

        @Test
        void gatewayUnavailable_leavesAsRefundInitiated() {
            when(paymentGateway.createRefund(anyString(), any()))
                    .thenReturn(CompletableFuture.failedFuture(
                            new PaymentGatewayUnavailableException("Stripe down")));

            paymentService.processRefund(completedPayment.getBookingId());

            PaymentEntity payment = findPaymentById(completedPayment.getId());
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_INITIATED);

            // No outbox event since refund didn't complete
            List<Map<String, Object>> outboxRows = jdbcTemplate.queryForList(
                    "SELECT * FROM outbox_events WHERE aggregate_id = ?",
                    completedPayment.getId().toString());
            assertThat(outboxRows).isEmpty();
        }

        @Test
        void gatewayBusinessError_leavesAsRefundInitiated() {
            when(paymentGateway.createRefund(anyString(), any()))
                    .thenReturn(CompletableFuture.failedFuture(
                            new PaymentGatewayException("Refund not allowed", null)));

            paymentService.processRefund(completedPayment.getBookingId());

            PaymentEntity payment = findPaymentById(completedPayment.getId());
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_INITIATED);
        }

        @Test
        void idempotent_secondRefundCallSkips() {
            // First refund succeeds
            paymentService.processRefund(completedPayment.getBookingId());

            PaymentEntity refunded = findPaymentById(completedPayment.getId());
            assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);

            // Reset mock to track second call
            reset(paymentGateway);

            // Second refund call — payment is now REFUNDED, not COMPLETED → should skip
            paymentService.processRefund(completedPayment.getBookingId());

            verify(paymentGateway, never()).createRefund(anyString(), any());
        }

        @Test
        void gatewayUnexpectedStatus_leavesAsRefundInitiated() {
            when(paymentGateway.createRefund(anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(
                            new GatewayRefundResponse("re_pending", "pending")));

            paymentService.processRefund(completedPayment.getBookingId());

            PaymentEntity payment = findPaymentById(completedPayment.getId());
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_INITIATED);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // retryPayment
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    class RetryPayment {

        private PaymentEntity pendingRetryPayment;

        @BeforeEach
        void createPendingRetryPayment() {
            when(paymentGateway.createPaymentIntent(any(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.failedFuture(
                            new PaymentGatewayUnavailableException("Circuit breaker open")));

            String bookingId = "booking-retry-" + UUID.randomUUID();
            pendingRetryPayment = paymentService.processPayment(bookingId, USER_ID, AMOUNT, CURRENCY);
            assertThat(pendingRetryPayment.getStatus()).isEqualTo(PaymentStatus.PENDING_RETRY);

            // Restore default mock for the retry attempt
            when(paymentGateway.createPaymentIntent(any(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.completedFuture(
                            new GatewayPaymentResponse("pi_retry_123", "requires_confirmation", "card")));
            when(paymentGateway.confirmPayment(anyString()))
                    .thenReturn(CompletableFuture.completedFuture(
                            new GatewayPaymentResponse("pi_retry_123", "succeeded", "card")));
        }

        @Test
        void success_completesPayment() {
            paymentService.retryPayment(pendingRetryPayment);

            PaymentEntity result = findPaymentById(pendingRetryPayment.getId());
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        void success_createsOutboxEvent() {
            paymentService.retryPayment(pendingRetryPayment);

            List<Map<String, Object>> outboxRows = jdbcTemplate.queryForList(
                    "SELECT * FROM outbox_events WHERE aggregate_id = ? AND event_type = 'PaymentCompleted'",
                    pendingRetryPayment.getId().toString());
            assertThat(outboxRows).hasSize(1);
        }

        @Test
        void incrementsRetryCount() {
            paymentService.retryPayment(pendingRetryPayment);

            Integer retryCount = jdbcTemplate.queryForObject(
                    "SELECT retry_count FROM payments WHERE id = ?",
                    Integer.class, pendingRetryPayment.getId());
            assertThat(retryCount).isEqualTo(1);
        }

        @Test
        void gatewayStillDown_remainsPendingRetry() {
            when(paymentGateway.createPaymentIntent(any(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.failedFuture(
                            new PaymentGatewayUnavailableException("Still down")));

            paymentService.retryPayment(pendingRetryPayment);

            PaymentEntity result = findPaymentById(pendingRetryPayment.getId());
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING_RETRY);
        }

        @Test
        void maxRetriesExhausted_marksAsFailed() {
            // Set retry_count to max_retries - 1 so the next attempt exhausts it
            jdbcTemplate.update(
                    "UPDATE payments SET retry_count = max_retries - 1 WHERE id = ?",
                    pendingRetryPayment.getId());

            when(paymentGateway.createPaymentIntent(any(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.failedFuture(
                            new PaymentGatewayUnavailableException("Gateway down")));

            paymentService.retryPayment(pendingRetryPayment);

            PaymentEntity result = findPaymentById(pendingRetryPayment.getId());
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(result.getFailureReason()).contains("Max retries exhausted");
        }

        @Test
        void externalPaymentIdPresent_skipsCreatePaymentIntent() {
            // Manually set externalPaymentId as if createPaymentIntent succeeded on first try
            jdbcTemplate.update(
                    "UPDATE payments SET external_payment_id = 'pi_existing_123' WHERE id = ?",
                    pendingRetryPayment.getId());
            pendingRetryPayment.setExternalPaymentId("pi_existing_123");

            // Reset to clear the @BeforeEach createPaymentIntent interaction, then re-stub confirm
            reset(paymentGateway);
            when(paymentGateway.confirmPayment(anyString()))
                    .thenReturn(CompletableFuture.completedFuture(
                            new GatewayPaymentResponse("pi_existing_123", "succeeded", "card")));

            paymentService.retryPayment(pendingRetryPayment);

            // createPaymentIntent should NOT be called — only confirmPayment
            verify(paymentGateway, never()).createPaymentIntent(any(), anyString(), anyString());
            verify(paymentGateway).confirmPayment("pi_existing_123");
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private PaymentEntity findPaymentById(UUID id) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM payments WHERE id = ?", id);

        return PaymentEntity.builder()
                .id((UUID) row.get("id"))
                .bookingId((String) row.get("booking_id"))
                .userId((String) row.get("user_id"))
                .amount((BigDecimal) row.get("amount"))
                .currency((String) row.get("currency"))
                .status(PaymentStatus.valueOf((String) row.get("status")))
                .externalPaymentId((String) row.get("external_payment_id"))
                .paymentMethod((String) row.get("payment_method"))
                .failureReason((String) row.get("failure_reason"))
                .retryCount(row.get("retry_count") != null ? (int) row.get("retry_count") : 0)
                .maxRetries(row.get("max_retries") != null ? (int) row.get("max_retries") : 3)
                .build();
    }
}
