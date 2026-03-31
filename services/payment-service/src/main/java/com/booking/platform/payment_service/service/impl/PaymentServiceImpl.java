package com.booking.platform.payment_service.service.impl;

import com.booking.platform.payment_service.constants.BkgConstants;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/**
    * Implementation of {@link PaymentService} that orchestrates the payment processing flow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentGateway paymentGateway;
    private final ObjectMapper objectMapper;

    @Value("${payment.retry.max-attempts:3}")
    private int maxRetries;

    @Value("${payment.retry.backoff-base-seconds:60}")
    private long backoffBaseSeconds;

    @Value("${payment.retry.backoff-multiplier:2}")
    private double backoffMultiplier;

    @Override
    public PaymentEntity processPayment(String bookingId, String userId, BigDecimal amount, String currency) {
        // Step 1: Idempotency check
        Optional<PaymentEntity> existing = paymentRepository.findByIdempotencyKey(bookingId);
        if (existing.isPresent()) {
            log.info("Duplicate payment request for bookingId='{}' — returning existing payment id='{}'",
                    bookingId, existing.get().getId());
            return existing.get();
        }

        // Step 2: Create payment record
        PaymentEntity payment = createPaymentRecord(bookingId, userId, amount, currency);
        log.info("Payment INITIATED: id='{}', bookingId='{}', amount={} {}",
                payment.getId(), bookingId, amount, currency);

        // Steps 3–8: Gateway interaction + outbox event writing
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
            logPaymentError(payment.getId(), bookingId, e);

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
            logPaymentError(paymentId, bookingId, cause);
            return;
        }
        if (cause instanceof PaymentGatewayException) {
            logPaymentError(paymentId, bookingId, cause);
            return;
        }
        String reason = cause != null ? cause.getMessage() : "Unknown error";
        log.error("Refund FAILED (unexpected): paymentId='{}', bookingId='{}', reason='{}'",
                paymentId, bookingId, reason);
    }

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
            logPaymentError(paymentId, bookingId, cause);
            return payment;
        }

        // Unexpected error — outbox event written inside markFailed() (P4-04)
        String reason = cause != null ? cause.getMessage() : "Unknown error";
        PaymentEntity payment = markFailed(paymentId, reason);
        log.error("Payment FAILED (unexpected): id='{}', bookingId='{}', reason='{}'",
                paymentId, bookingId, reason);
        return payment;
    }

    private void logPaymentError(final UUID paymentId, String bookingId, Throwable cause) {
        log.error("Payment FAILED (gateway error): id='{}', bookingId='{}', reason='{}'",
                paymentId, bookingId, cause.getMessage());
    }

    @Transactional
    protected PaymentEntity createPaymentRecord(String bookingId, String userId, BigDecimal amount, String currency) {
        PaymentEntity payment = PaymentEntity.builder()
                .bookingId(bookingId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .status(PaymentStatus.INITIATED)
                .idempotencyKey(bookingId)
                .maxRetries(maxRetries)
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

        // Write outbox event in the same transaction as the status change.
        // The OutboxPollingPublisher will read this and publish to Kafka.
        saveOutboxEvent(payment, BkgConstants.BkgOutboxConstants.PAYMENT_COMPLETED_EVENT,
                buildPayload(payment, null, BkgConstants.BkgOutboxConstants.PAYMENT_COMPLETED_EVENT));
        return payment;
    }

    @Transactional
    protected PaymentEntity markFailed(UUID paymentId, String reason) {
        PaymentEntity payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        payment = paymentRepository.save(payment);

        // Write outbox event in the same transaction as the status change.
        // The OutboxPollingPublisher will read this and publish to Kafka.
        saveOutboxEvent(payment, BkgConstants.BkgOutboxConstants.PAYMENT_FAILED_EVENT,
                buildPayload(payment, null, BkgConstants.BkgOutboxConstants.PAYMENT_FAILED_EVENT));
        return payment;
    }

    @Transactional
    protected PaymentEntity markPendingRetry(UUID paymentId, String reason) {
        PaymentEntity payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.setStatus(PaymentStatus.PENDING_RETRY);
        payment.setFailureReason(reason);
        payment.setNextRetryAt(Instant.now().plusSeconds(computeBackoffSeconds(payment.getRetryCount())));
        return paymentRepository.save(payment);
    }

    /**
     * Retries a PENDING_RETRY payment. Called by the scheduler — not part of the public interface.
     *
     * <p>Flow:
     * <ol>
     *   <li>Increment retryCount, clear nextRetryAt, set status = PROCESSING</li>
     *   <li>If externalPaymentId is null: createPaymentIntent, then confirmPayment</li>
     *   <li>If externalPaymentId is set: confirmPayment only (intent already exists on gateway)</li>
     *   <li>On success: markCompleted (writes outbox event)</li>
     *   <li>On business failure: markFailed (writes outbox event)</li>
     *   <li>On gateway unavailable: if retries exhausted → markFailed, else → markPendingRetry with next backoff</li>
     * </ol>
     */
    @Transactional
    protected void incrementRetryCount(UUID paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.setRetryCount(payment.getRetryCount() + 1);
        payment.setNextRetryAt(null);
        payment.setStatus(PaymentStatus.PROCESSING);
        paymentRepository.save(payment);
    }

    public void retryPayment(PaymentEntity snapshot) {
        // Step 1: Atomically increment retryCount and move to PROCESSING
        incrementRetryCount(snapshot.getId());

        // Reload to get the fresh retryCount after the transaction above
        PaymentEntity payment = paymentRepository.findById(snapshot.getId()).orElseThrow();

        log.info("Payment RETRY attempt {}/{}: id='{}', bookingId='{}'",
                payment.getRetryCount(), payment.getMaxRetries(), payment.getId(), payment.getBookingId());

        try {
            GatewayPaymentResponse confirmResponse;

            if (payment.getExternalPaymentId() == null) {
                // Intent was never created — start from scratch
                GatewayPaymentResponse createResponse =
                        paymentGateway.createPaymentIntent(payment.getAmount(), payment.getCurrency(), payment.getBookingId()).join();
                updateToProcessing(payment.getId(), createResponse);
                confirmResponse = paymentGateway.confirmPayment(createResponse.externalPaymentId()).join();
            } else {
                // Intent already exists on the gateway — just confirm
                confirmResponse = paymentGateway.confirmPayment(payment.getExternalPaymentId()).join();
            }

            if ("succeeded".equals(confirmResponse.status())) {
                markCompleted(payment.getId(), confirmResponse);
                log.info("Payment COMPLETED (after retry): id='{}', bookingId='{}'",
                        payment.getId(), payment.getBookingId());
            } else {
                markFailed(payment.getId(), "Gateway returned status: " + confirmResponse.status());
                log.warn("Payment FAILED after retry (unexpected status): id='{}', status='{}'",
                        payment.getId(), confirmResponse.status());
            }

        } catch (CompletionException e) {
            handleRetryGatewayException(payment, e.getCause());
        } catch (PaymentGatewayUnavailableException e) {
            handleRetryGatewayException(payment, e);
        } catch (PaymentGatewayException e) {
            markFailed(payment.getId(), e.getMessage());
            logPaymentError(payment.getId(), payment.getBookingId(), e);
        }
    }

    private void handleRetryGatewayException(PaymentEntity payment, Throwable cause) {
        if (cause instanceof PaymentGatewayUnavailableException) {
            if (payment.getRetryCount() >= payment.getMaxRetries()) {
                markFailed(payment.getId(), "Max retries exhausted: " + cause.getMessage());
                log.warn("Payment FAILED (max retries exhausted): id='{}', bookingId='{}', attempts={}",
                        payment.getId(), payment.getBookingId(), payment.getRetryCount());
            } else {
                markPendingRetry(payment.getId(), cause.getMessage());
                log.warn("Payment PENDING_RETRY (attempt {}/{}): id='{}', bookingId='{}'",
                        payment.getRetryCount(), payment.getMaxRetries(),
                        payment.getId(), payment.getBookingId());
            }
            return;
        }
        // Business failure or unexpected error
        String reason = cause != null ? cause.getMessage() : "Unknown error";
        markFailed(payment.getId(), reason);
        log.error("Payment FAILED during retry: id='{}', bookingId='{}', reason='{}'",
                payment.getId(), payment.getBookingId(), reason);
    }

    private long computeBackoffSeconds(int retryCount) {
        return Math.min((long) (backoffBaseSeconds * Math.pow(backoffMultiplier, retryCount)), 3600);
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

        saveOutboxEvent(payment, BkgConstants.BkgOutboxConstants.REFUND_COMPLETED_EVENT,
                buildPayload(payment, response.refundId(),
                BkgConstants.BkgOutboxConstants.REFUND_COMPLETED_EVENT));

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

    private String buildPayload(PaymentEntity payment, String refundId, String eventType) {
        ObjectNode node = objectMapper.createObjectNode();
        if (BkgConstants.BkgOutboxConstants.REFUND_COMPLETED_EVENT.equals(eventType)) {
            node.put(BkgConstants.BkgOutboxConstants.REFUND_ID, refundId);
        }
        if (BkgConstants.BkgOutboxConstants.PAYMENT_FAILED_EVENT.equals(eventType)) {
            node.put(BkgConstants.BkgOutboxConstants.REASON, payment.getFailureReason() != null ? payment.getFailureReason() : BkgConstants.BkgOutboxConstants.UNKNOWN);
        } else {
            node.put(BkgConstants.BkgOutboxConstants.AMOUNT, payment.getAmount().doubleValue());
            node.put(BkgConstants.BkgOutboxConstants.CURRENCY, payment.getCurrency());
        }
        node.put(BkgConstants.BkgOutboxConstants.PAYMENT_ID, payment.getId().toString());
        node.put(BkgConstants.BkgOutboxConstants.BOOKING_ID, payment.getBookingId());
        node.put(BkgConstants.BkgOutboxConstants.TIMESTAMP, Instant.now().toString());
        return node.toString();
    }
}
