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
import com.booking.platform.payment_service.validation.PaymentValidator;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.booking.platform.common.logging.ApplicationLogger;
import com.booking.platform.common.logging.LogErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
    private final PaymentValidator paymentValidator;
    private final Clock clock;

    @Value("${payment.retry.max-attempts:3}")
    private int maxRetries;

    // ── Payment creation ──────────────────────────────────────────────────────

    /**
     * Creates the payment record for an <b>order</b> covering one or more bookings. The
     * idempotency key is the client-generated order id; {@code bookingId} stores that order id
     * (the payment's primary handle), and {@code bookingIds} holds the covered bookings.
     */
    @Transactional
    public PaymentEntity createOrderPaymentRecord(String orderId, String userId, List<String> bookingIds,
                                                  BigDecimal amount, String currency) {
        PaymentEntity payment = PaymentEntity.builder()
                .bookingId(orderId)
                .bookingIds(String.join(",", bookingIds))
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .status(PaymentStatus.INITIATED)
                .idempotencyKey(orderId)
                .maxRetries(maxRetries)
                .build();
        ApplicationLogger.logMessage(log, Level.DEBUG,
                "Creating order payment record: orderId='{}', userId='{}', bookings={}, amount={}, currency='{}'",
                orderId, userId, bookingIds, amount, currency);
        return paymentRepository.save(payment);
    }

    // ── In-progress transitions ───────────────────────────────────────────────

    @Transactional
    public PaymentEntity updateToProcessing(UUID paymentId, GatewayPaymentResponse response) {
        PaymentEntity payment = findOrThrow(paymentId);
        paymentValidator.assertValidTransition(payment, PaymentStatus.PROCESSING);
        payment.setExternalPaymentId(response.externalPaymentId());
        payment.setPaymentMethod(response.paymentMethod());
        payment.setStatus(PaymentStatus.PROCESSING);
        ApplicationLogger.logMessage(log, Level.DEBUG,
                "Payment id='{}' moved to PROCESSING with externalPaymentId='{}'",
                paymentId, response.externalPaymentId());
        return paymentRepository.save(payment);
    }

    // ── Terminal state transitions (write outbox event in same transaction) ───

    @Transactional
    public PaymentEntity markCompleted(UUID paymentId, GatewayPaymentResponse response) {
        PaymentEntity payment = findOrThrow(paymentId);
        paymentValidator.assertValidTransition(payment, PaymentStatus.COMPLETED);
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
        paymentValidator.assertValidTransition(payment, PaymentStatus.FAILED);
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        payment = paymentRepository.save(payment);
        saveOutboxEvent(payment, BkgConstants.BkgOutboxConstants.PAYMENT_FAILED_EVENT,
                buildPayload(payment, null, BkgConstants.BkgOutboxConstants.PAYMENT_FAILED_EVENT));
        return payment;
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
            ApplicationLogger.logMessage(log, Level.WARN, LogErrorCode.PAYMENT_REFUND_FAILED,
                    "Payment id='{}' is no longer COMPLETED (status={}), skipping refund initiation",
                    paymentId, payment.getStatus());
            return payment;
        }
        payment.setStatus(PaymentStatus.REFUND_INITIATED);
        ApplicationLogger.logMessage(log, Level.DEBUG, "Payment id='{}' marked as REFUND_INITIATED", paymentId);
        return paymentRepository.save(payment);
    }

    @Transactional
    public PaymentEntity markRefunded(UUID paymentId, GatewayRefundResponse response) {
        PaymentEntity payment = findOrThrow(paymentId);
        paymentValidator.assertValidTransition(payment, PaymentStatus.REFUNDED);
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
                .aggregateType(BkgConstants.BkgOutboxConstants.AGGREGATE_TYPE_PAYMENT)
                .aggregateId(payment.getId().toString())
                .eventType(eventType)
                .payload(payload)
                .build();
        outboxEventRepository.save(outboxEvent);
        ApplicationLogger.logMessage(log, Level.DEBUG, "Outbox event saved: type='{}', aggregateId='{}'", eventType, payment.getId());
    }

    private String buildPayload(PaymentEntity payment, String refundId, String eventType) {
        ObjectNode node = objectMapper.createObjectNode();

        // Event-specific fields
        switch (eventType) {
            case BkgConstants.BkgOutboxConstants.PAYMENT_COMPLETED_EVENT ->
                // Store as plain string to preserve full decimal precision through the JSON hop
                node.put(BkgConstants.BkgOutboxConstants.AMOUNT, payment.getAmount().toPlainString())
                    .put(BkgConstants.BkgOutboxConstants.CURRENCY, payment.getCurrency());

            case BkgConstants.BkgOutboxConstants.PAYMENT_FAILED_EVENT ->
                node.put(BkgConstants.BkgOutboxConstants.REASON,
                        payment.getFailureReason() != null
                                ? payment.getFailureReason()
                                : BkgConstants.BkgOutboxConstants.UNKNOWN);

            case BkgConstants.BkgOutboxConstants.REFUND_COMPLETED_EVENT ->
                node.put(BkgConstants.BkgOutboxConstants.REFUND_ID, refundId)
                    .put(BkgConstants.BkgOutboxConstants.AMOUNT, payment.getAmount().toPlainString())
                    .put(BkgConstants.BkgOutboxConstants.CURRENCY, payment.getCurrency());

            default -> throw new IllegalArgumentException("Unknown event type for payload: " + eventType);
        }

        // Common fields present in every event
        node.put(BkgConstants.BkgOutboxConstants.PAYMENT_ID, payment.getId().toString());
        node.put(BkgConstants.BkgOutboxConstants.BOOKING_ID, payment.getBookingId());
        // booking_ids: all bookings this payment covers (an order). Falls back to the single
        // booking so single-booking payments still emit a one-element list.
        ArrayNode bookingIds = node.putArray(BkgConstants.BkgOutboxConstants.BOOKING_IDS);
        if (payment.getBookingIds() != null && !payment.getBookingIds().isBlank()) {
            for (String id : payment.getBookingIds().split(",")) {
                bookingIds.add(id);
            }
        } else {
            bookingIds.add(payment.getBookingId());
        }
        node.put(BkgConstants.BkgOutboxConstants.TIMESTAMP, Instant.now(clock).toString());
        return node.toString();
    }

}
