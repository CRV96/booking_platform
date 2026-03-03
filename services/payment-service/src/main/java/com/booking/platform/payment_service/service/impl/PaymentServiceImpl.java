package com.booking.platform.payment_service.service.impl;

import com.booking.platform.payment_service.dto.GatewayPaymentResponse;
import com.booking.platform.payment_service.dto.GatewayRefundResponse;
import com.booking.platform.payment_service.entity.OutboxEventEntity;
import com.booking.platform.payment_service.entity.PaymentEntity;
import com.booking.platform.payment_service.entity.enums.PaymentStatus;
import com.booking.platform.payment_service.exception.PaymentGatewayException;
import com.booking.platform.payment_service.exception.PaymentGatewayUnavailableException;
import com.booking.platform.payment_service.gateway.PaymentGateway;
import com.booking.platform.payment_service.repository.OutboxEventRepository;
import com.booking.platform.payment_service.repository.PaymentRepository;
import com.booking.platform.payment_service.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/**
 * Implementation of {@link PaymentService}.
 *
 * <p>Flow:
 * <ol>
 *   <li>Idempotency check — if a payment with this bookingId already exists, return it</li>
 *   <li>Create {@link PaymentEntity} with status {@code INITIATED}, persist</li>
 *   <li>Call {@link PaymentGateway#createPaymentIntent} (outside transaction to avoid holding DB connection)</li>
 *   <li>Update entity with external ID + status {@code PROCESSING}, persist</li>
 *   <li>Call {@link PaymentGateway#confirmPayment} (test mode auto-confirms with pm_card_visa)</li>
 *   <li>On success → status {@code COMPLETED}, persist + insert outbox event (same transaction)</li>
 *   <li>On business failure → status {@code FAILED}, persist + insert outbox event (same transaction)</li>
 *   <li>On infrastructure failure → status {@code PENDING_RETRY} (P4-03 circuit breaker / timeout)</li>
 * </ol>
 *
 * <p>Gateway calls are made outside {@code @Transactional} blocks to avoid holding
 * database connections during potentially slow HTTP calls to Stripe.
 *
 * <p>Gateway methods return {@link java.util.concurrent.CompletableFuture} (required by
 * Resilience4j's {@code @TimeLimiter}). We call {@code .join()} to block, then unwrap
 * the {@link CompletionException} to inspect the root cause.
 *
 * <p><b>Transactional Outbox (P4-04):</b> Events are no longer published directly to Kafka.
 * Instead, an {@link OutboxEventEntity} is written to the {@code outbox_events} table in the
 * same {@code @Transactional} method that updates the payment status. A separate poller
 * ({@link com.booking.platform.payment_service.messaging.publisher.impl.OutboxPollingPublisher})
 * reads unpublished events and publishes them to Kafka, guaranteeing at-least-once delivery.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentGateway paymentGateway;
    private final ObjectMapper objectMapper;

    @Override
    public PaymentEntity processPayment(String bookingId, String userId, BigDecimal amount, String currency) {
        // ── Step 1: Idempotency check ──────────────────────────────────────────
        Optional<PaymentEntity> existing = paymentRepository.findByIdempotencyKey(bookingId);
        if (existing.isPresent()) {
            log.info("Duplicate payment request for bookingId='{}' — returning existing payment id='{}'",
                    bookingId, existing.get().getId());
            return existing.get();
        }

        // ── Step 2: Create payment record ──────────────────────────────────────
        PaymentEntity payment = createPaymentRecord(bookingId, userId, amount, currency);
        log.info("Payment INITIATED: id='{}', bookingId='{}', amount={} {}",
                payment.getId(), bookingId, amount, currency);

        // ── Steps 3–8: Gateway interaction + outbox event writing ────────────
        try {
            // Step 3: Create payment intent on gateway (.join() blocks on the future)
            GatewayPaymentResponse createResponse =
                    paymentGateway.createPaymentIntent(amount, currency, bookingId).join();

            // Step 4: Update entity with external ID, move to PROCESSING
            payment = updateToProcessing(payment.getId(), createResponse);
            log.info("Payment PROCESSING: id='{}', externalId='{}'",
                    payment.getId(), createResponse.externalPaymentId());

            // Step 5: Confirm the payment (test mode auto-confirms)
            GatewayPaymentResponse confirmResponse =
                    paymentGateway.confirmPayment(createResponse.externalPaymentId()).join();

            // Step 6: Check result and update accordingly
            // Note: outbox events are written inside the @Transactional helper methods (P4-04).
            // The OutboxPollingPublisher reads and publishes them to Kafka asynchronously.
            if ("succeeded".equals(confirmResponse.status())) {
                payment = markCompleted(payment.getId(), confirmResponse);
                log.info("Payment COMPLETED: id='{}', bookingId='{}'", payment.getId(), bookingId);
            } else {
                payment = markFailed(payment.getId(), "Gateway returned status: " + confirmResponse.status());
                log.warn("Payment FAILED (unexpected status): id='{}', status='{}'",
                        payment.getId(), confirmResponse.status());
            }

        } catch (CompletionException e) {
            // CompletableFuture.join() wraps exceptions in CompletionException — unwrap
            payment = handleGatewayException(payment.getId(), bookingId, e.getCause());
        } catch (PaymentGatewayUnavailableException e) {
            // Direct throw (not wrapped) — gateway unavailable, mark for retry
            payment = markPendingRetry(payment.getId(), e.getMessage());
            log.warn("Payment PENDING_RETRY: id='{}', bookingId='{}', reason='{}'",
                    payment.getId(), bookingId, e.getMessage());
        } catch (PaymentGatewayException e) {
            // Direct throw (not wrapped) — business failure
            // Outbox event written inside markFailed() (P4-04)
            payment = markFailed(payment.getId(), e.getMessage());
            log.error("Payment FAILED (gateway error): id='{}', bookingId='{}', reason='{}'",
                    payment.getId(), bookingId, e.getMessage());
        }

        return payment;
    }

    // ── Refund flow (P4-05) ─────────────────────────────────────────────────

    @Override
    public void processRefund(String bookingId) {
        // Step 1: Look up the payment
        Optional<PaymentEntity> optional = paymentRepository.findByBookingId(bookingId);
        if (optional.isEmpty()) {
            log.warn("No payment found for bookingId='{}', cannot process refund", bookingId);
            return;
        }

        PaymentEntity payment = optional.get();

        // Step 2: Guard — only COMPLETED payments can be refunded
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            log.info("Payment id='{}' for bookingId='{}' is not COMPLETED (status={}), skipping refund",
                    payment.getId(), bookingId, payment.getStatus());
            return;
        }

        // Step 3: Mark REFUND_INITIATED (transactional)
        payment = markRefundInitiated(payment.getId());
        log.info("Payment REFUND_INITIATED: id='{}', bookingId='{}'", payment.getId(), bookingId);

        // Step 4: Call gateway refund (outside transaction)
        try {
            GatewayRefundResponse refundResponse =
                    paymentGateway.createRefund(payment.getExternalPaymentId(), payment.getAmount()).join();

            if ("succeeded".equals(refundResponse.status())) {
                payment = markRefunded(payment.getId(), refundResponse);
                log.info("Payment REFUNDED: id='{}', bookingId='{}', refundId='{}'",
                        payment.getId(), bookingId, refundResponse.refundId());
            } else {
                // TODO: Unexpected status — leave as REFUND_INITIATED for investigation
                log.warn("Refund returned unexpected status '{}' for payment id='{}', leaving as REFUND_INITIATED",
                        refundResponse.status(), payment.getId());
            }

        } catch (CompletionException e) {
            handleRefundException(payment.getId(), bookingId, e.getCause());
        } catch (PaymentGatewayUnavailableException e) {
            // Leave as REFUND_INITIATED — can be retried later
            log.warn("Refund PENDING (gateway unavailable): paymentId='{}', bookingId='{}', reason='{}'",
                    payment.getId(), bookingId, e.getMessage());
        } catch (PaymentGatewayException e) {
            // Leave as REFUND_INITIATED — refunds must eventually succeed
            log.error("Refund FAILED (gateway error): paymentId='{}', bookingId='{}', reason='{}'",
                    payment.getId(), bookingId, e.getMessage());
        }
    }

    // ── Refund exception handling ─────────────────────────────────────────────

    private void handleRefundException(UUID paymentId, String bookingId, Throwable cause) {
        if (cause instanceof PaymentGatewayUnavailableException) {
            log.warn("Refund PENDING (gateway unavailable): paymentId='{}', bookingId='{}', reason='{}'",
                    paymentId, bookingId, cause.getMessage());
            return;
        }
        if (cause instanceof PaymentGatewayException) {
            log.error("Refund FAILED (gateway error): paymentId='{}', bookingId='{}', reason='{}'",
                    paymentId, bookingId, cause.getMessage());
            return;
        }
        String reason = cause != null ? cause.getMessage() : "Unknown error";
        log.error("Refund FAILED (unexpected): paymentId='{}', bookingId='{}', reason='{}'",
                paymentId, bookingId, reason);
    }

    // ── Payment exception handling ────────────────────────────────────────────

    private PaymentEntity handleGatewayException(UUID paymentId, String bookingId, Throwable cause) {
        if (cause instanceof PaymentGatewayUnavailableException) {
            // Infrastructure failure — gateway down, circuit open, timeout, bulkhead full
            PaymentEntity payment = markPendingRetry(paymentId, cause.getMessage());
            log.warn("Payment PENDING_RETRY (gateway unavailable): id='{}', bookingId='{}', reason='{}'",
                    paymentId, bookingId, cause.getMessage());
            return payment;
        }

        if (cause instanceof PaymentGatewayException) {
            // Business failure — card declined, invalid amount, etc.
            // Outbox event written inside markFailed() (P4-04)
            PaymentEntity payment = markFailed(paymentId, cause.getMessage());
            log.error("Payment FAILED (gateway error): id='{}', bookingId='{}', reason='{}'",
                    paymentId, bookingId, cause.getMessage());
            return payment;
        }

        // Unexpected error — outbox event written inside markFailed() (P4-04)
        String reason = cause != null ? cause.getMessage() : "Unknown error";
        PaymentEntity payment = markFailed(paymentId, reason);
        log.error("Payment FAILED (unexpected): id='{}', bookingId='{}', reason='{}'",
                paymentId, bookingId, reason);
        return payment;
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    @Transactional
    protected PaymentEntity createPaymentRecord(String bookingId, String userId, BigDecimal amount, String currency) {
        PaymentEntity payment = PaymentEntity.builder()
                .bookingId(bookingId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .status(PaymentStatus.INITIATED)
                .idempotencyKey(bookingId)
                .build();
        return paymentRepository.save(payment);
    }

    @Transactional
    protected PaymentEntity updateToProcessing(UUID paymentId, GatewayPaymentResponse response) {
        PaymentEntity payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.setExternalPaymentId(response.externalPaymentId());
        payment.setPaymentMethod(response.paymentMethod());
        payment.setStatus(PaymentStatus.PROCESSING);
        return paymentRepository.save(payment);
    }

    @Transactional
    protected PaymentEntity markCompleted(UUID paymentId, GatewayPaymentResponse response) {
        PaymentEntity payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaymentMethod(response.paymentMethod());
        payment = paymentRepository.save(payment);

        // P4-04: Write outbox event in the same transaction as the status change.
        // The OutboxPollingPublisher will read this and publish to Kafka.
        saveOutboxEvent(payment, "PaymentCompleted", buildCompletedPayload(payment));
        return payment;
    }

    @Transactional
    protected PaymentEntity markFailed(UUID paymentId, String reason) {
        PaymentEntity payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        payment = paymentRepository.save(payment);

        // P4-04: Write outbox event in the same transaction as the status change.
        // The OutboxPollingPublisher will read this and publish to Kafka.
        saveOutboxEvent(payment, "PaymentFailed", buildFailedPayload(payment));
        return payment;
    }

    @Transactional
    protected PaymentEntity markPendingRetry(UUID paymentId, String reason) {
        PaymentEntity payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.setStatus(PaymentStatus.PENDING_RETRY);
        payment.setFailureReason(reason);
        return paymentRepository.save(payment);
    }

    @Transactional
    protected PaymentEntity markRefundInitiated(UUID paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.setStatus(PaymentStatus.REFUND_INITIATED);
        return paymentRepository.save(payment);
    }

    @Transactional
    protected PaymentEntity markRefunded(UUID paymentId, GatewayRefundResponse response) {
        PaymentEntity payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.setStatus(PaymentStatus.REFUNDED);
        payment = paymentRepository.save(payment);

        // P4-04: Write outbox event in the same transaction as the status change.
        saveOutboxEvent(payment, "RefundCompleted", buildRefundCompletedPayload(payment, response));
        return payment;
    }

    // ── Outbox helpers (P4-04) ──────────────────────────────────────────────────

    /**
     * Inserts an outbox event row. Called inside @Transactional methods so it
     * participates in the same DB transaction as the payment status change.
     */
    private void saveOutboxEvent(PaymentEntity payment, String eventType, String payload) {
        OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                .aggregateType("Payment")
                .aggregateId(payment.getId().toString())
                .eventType(eventType)
                .payload(payload)
                .build();
        outboxEventRepository.save(outboxEvent);
        log.debug("Outbox event saved: type='{}', aggregateId='{}'", eventType, payment.getId());
    }

    //TODO: Refactoring this class, a lot of duplicates
    /** Builds JSON payload for PaymentCompleted (mirrors PaymentCompletedEvent proto fields). */
    private String buildCompletedPayload(PaymentEntity payment) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("payment_id", payment.getId().toString());
        node.put("booking_id", payment.getBookingId());
        node.put("amount", payment.getAmount().doubleValue());
        node.put("currency", payment.getCurrency());
        node.put("timestamp", Instant.now().toString());
        return node.toString();
    }

    /** Builds JSON payload for PaymentFailed (mirrors PaymentFailedEvent proto fields). */
    private String buildFailedPayload(PaymentEntity payment) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("payment_id", payment.getId().toString());
        node.put("booking_id", payment.getBookingId());
        node.put("reason", payment.getFailureReason() != null ? payment.getFailureReason() : "Unknown");
        node.put("timestamp", Instant.now().toString());
        return node.toString();
    }

    /** Builds JSON payload for RefundCompleted (mirrors RefundCompletedEvent proto fields). */
    private String buildRefundCompletedPayload(PaymentEntity payment, GatewayRefundResponse response) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("payment_id", payment.getId().toString());
        node.put("booking_id", payment.getBookingId());
        node.put("refund_id", response.refundId());
        node.put("amount", payment.getAmount().doubleValue());
        node.put("currency", payment.getCurrency());
        node.put("timestamp", Instant.now().toString());
        return node.toString();
    }
}
