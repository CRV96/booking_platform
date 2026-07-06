package com.booking.platform.payment_service.service;

import com.booking.platform.payment_service.entity.PaymentEntity;

import java.math.BigDecimal;

/**
 * Core payment processing service.
 *
 * <p>Orchestrates the full payment lifecycle:
 * <ol>
 *   <li>Persist a payment record (idempotent — returns existing if duplicate)</li>
 *   <li>Call the payment gateway to create and confirm the charge</li>
 *   <li>Update the payment record with the result</li>
 *   <li>Publish the appropriate Kafka event (completed or failed)</li>
 * </ol>
 */
public interface PaymentService {

    /**
     * Processes a payment for a booking.
     *
     * <p>Idempotent: if a payment with the same {@code bookingId} as idempotency key
     * already exists, returns the existing record without reprocessing.
     *
     * @param bookingId the booking this payment is for (also used as idempotency key)
     * @param userId    Keycloak subject of the user
     * @param amount    payment amount in standard currency units (must be positive)
     * @param currency  ISO 4217 currency code (must be exactly 3 characters)
     * @return the persisted payment entity
     * @throws IllegalArgumentException if amount is null/non-positive or currency is not 3 characters
     */
    PaymentEntity processPayment(String bookingId, String userId, BigDecimal amount, String currency);

    /**
     * Processes a refund for a completed payment.
     *
     * <p>Finds the payment by bookingId. If the payment is not in COMPLETED status,
     * logs and returns (no refund needed). Otherwise:
     * <ol>
     *   <li>COMPLETED → REFUND_INITIATED (persisted atomically with status re-check)</li>
     *   <li>Calls gateway.createRefund() outside transaction</li>
     *   <li>On success: REFUND_INITIATED → REFUNDED + outbox event "RefundCompleted"</li>
     *   <li>On gateway unavailable: stays REFUND_INITIATED for manual retry</li>
     * </ol>
     *
     * @param bookingId the booking whose payment should be refunded
     */
    void processRefund(String bookingId);

    /**
     * Retries a payment currently in {@code PENDING_RETRY} status.
     *
     * <p>Called by {@code PaymentRetryScheduler} for payments whose backoff window has elapsed.
     * Increments the retry counter, re-attempts the gateway call, and transitions to
     * {@code COMPLETED}, {@code PENDING_RETRY} (with next backoff), or {@code FAILED}
     * (when max retries is reached).
     *
     * @param payment the payment entity snapshot returned by the scheduler query
     */
    void retryPayment(PaymentEntity payment);
}
