package com.booking.platform.payment_service.service.impl;

import com.booking.platform.payment_service.constants.BkgConstants;
import com.booking.platform.payment_service.dto.GatewayPaymentResponse;
import com.booking.platform.payment_service.dto.GatewayRefundResponse;
import com.booking.platform.payment_service.entity.OutboxEventEntity;
import com.booking.platform.payment_service.entity.PaymentEntity;
import com.booking.platform.payment_service.entity.enums.PaymentStatus;
import com.booking.platform.payment_service.exception.PaymentNotFoundException;
import com.booking.platform.payment_service.repository.OutboxEventRepository;
import com.booking.platform.payment_service.repository.PaymentRepository;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Handles all atomic payment state transitions.
 *
 * <p>Each method is a single database transaction that updates the payment status
 * and writes an outbox event in the same commit — guaranteeing atomicity between
 * the status change and the Kafka event notification.
 *
 * <p><b>Why a separate bean?</b> Spring's {@code @Transactional} is proxy-based.
 * Self-invocation (calling {@code this.markCompleted()} from within the same bean)
 * bypasses the proxy and the annotation has no effect. By extracting state transitions
 * into this bean, every call goes through the Spring proxy and the transaction boundary
 * is correctly applied.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentStateTransitionService {

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${payment.retry.max-attempts:3}")
    private int maxRetries;

    @Value("${payment.retry.backoff-base-seconds:60}")
    private long backoffBaseSeconds;

    @Value("${payment.retry.backoff-multiplier:2}")
    private double backoffMultiplier;

    // ── Payment creation ──────────────────────────────────────────────────────

    @Transactional
    public PaymentEntity createPaymentRecord(String bookingId, String userId, BigDecimal amount, String currency) {
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

    // ── In-progress transitions ───────────────────────────────────────────────

    @Transactional
    public PaymentEntity updateToProcessing(UUID paymentId, GatewayPaymentResponse response) {
        PaymentEntity payment = findOrThrow(paymentId);
        payment.setExternalPaymentId(response.externalPaymentId());
        payment.setPaymentMethod(response.paymentMethod());
        payment.setStatus(PaymentStatus.PROCESSING);
        return paymentRepository.save(payment);
    }

    /**
     * Atomically increments the retry counter, clears the next-retry timestamp,
     * and moves the payment to PROCESSING.
     *
     * @return the updated payment entity with the new retryCount
     */
    @Transactional
    public PaymentEntity incrementRetryCount(UUID paymentId) {
        PaymentEntity payment = findOrThrow(paymentId);
        payment.setRetryCount(payment.getRetryCount() + 1);
        payment.setNextRetryAt(null);
        payment.setStatus(PaymentStatus.PROCESSING);
        return paymentRepository.save(payment);
    }

    // ── Terminal state transitions (write outbox event in same transaction) ───

    @Transactional
    public PaymentEntity markCompleted(UUID paymentId, GatewayPaymentResponse response) {
        PaymentEntity payment = findOrThrow(paymentId);
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaymentMethod(response.paymentMethod());
        payment = paymentRepository.save(payment);
        saveOutboxEvent(payment, BkgConstants.BkgOutboxConstants.PAYMENT_COMPLETED_EVENT,
                buildPayload(payment, null, BkgConstants.BkgOutboxConstants.PAYMENT_COMPLETED_EVENT));
        return payment;
    }

    @Transactional
    public PaymentEntity markFailed(UUID paymentId, String reason) {
        PaymentEntity payment = findOrThrow(paymentId);
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        payment = paymentRepository.save(payment);
        saveOutboxEvent(payment, BkgConstants.BkgOutboxConstants.PAYMENT_FAILED_EVENT,
                buildPayload(payment, null, BkgConstants.BkgOutboxConstants.PAYMENT_FAILED_EVENT));
        return payment;
    }

    @Transactional
    public PaymentEntity markPendingRetry(UUID paymentId, String reason) {
        PaymentEntity payment = findOrThrow(paymentId);
        payment.setStatus(PaymentStatus.PENDING_RETRY);
        payment.setFailureReason(reason);
        payment.setNextRetryAt(Instant.now().plusSeconds(computeBackoffSeconds(payment.getRetryCount())));
        return paymentRepository.save(payment);
    }

    // ── Refund transitions ────────────────────────────────────────────────────

    /**
     * Atomically re-checks that the payment is still COMPLETED and moves it to
     * REFUND_INITIATED. If a concurrent call already changed the status, this
     * method logs and returns without modifying the record, preventing double-refunds.
     *
     * @return the updated payment (status may still be COMPLETED if the guard fired)
     */
    @Transactional
    public PaymentEntity markRefundInitiated(UUID paymentId) {
        PaymentEntity payment = findOrThrow(paymentId);
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            log.warn("Payment id='{}' is no longer COMPLETED (status={}), skipping refund initiation",
                    paymentId, payment.getStatus());
            return payment;
        }
        payment.setStatus(PaymentStatus.REFUND_INITIATED);
        return paymentRepository.save(payment);
    }

    @Transactional
    public PaymentEntity markRefunded(UUID paymentId, GatewayRefundResponse response) {
        PaymentEntity payment = findOrThrow(paymentId);
        payment.setStatus(PaymentStatus.REFUNDED);
        payment = paymentRepository.save(payment);
        saveOutboxEvent(payment, BkgConstants.BkgOutboxConstants.REFUND_COMPLETED_EVENT,
                buildPayload(payment, response.refundId(), BkgConstants.BkgOutboxConstants.REFUND_COMPLETED_EVENT));
        return payment;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private PaymentEntity findOrThrow(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

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
            node.put(BkgConstants.BkgOutboxConstants.REASON,
                    payment.getFailureReason() != null
                            ? payment.getFailureReason()
                            : BkgConstants.BkgOutboxConstants.UNKNOWN);
        } else {
            // Store as plain string to preserve full decimal precision through the JSON hop
            node.put(BkgConstants.BkgOutboxConstants.AMOUNT, payment.getAmount().toPlainString());
            node.put(BkgConstants.BkgOutboxConstants.CURRENCY, payment.getCurrency());
        }
        node.put(BkgConstants.BkgOutboxConstants.PAYMENT_ID, payment.getId().toString());
        node.put(BkgConstants.BkgOutboxConstants.BOOKING_ID, payment.getBookingId());
        node.put(BkgConstants.BkgOutboxConstants.TIMESTAMP, Instant.now().toString());
        return node.toString();
    }

    long computeBackoffSeconds(int retryCount) {
        return Math.min((long) (backoffBaseSeconds * Math.pow(backoffMultiplier, retryCount)), 3600);
    }
}
