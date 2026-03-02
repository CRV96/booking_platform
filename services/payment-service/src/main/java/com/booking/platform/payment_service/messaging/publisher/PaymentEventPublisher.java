package com.booking.platform.payment_service.messaging.publisher;

import com.booking.platform.payment_service.entity.PaymentEntity;

/**
 * Publishes payment lifecycle events to Kafka.
 *
 * <p>Each method maps to a terminal state transition in the payment lifecycle:
 * <ul>
 *   <li>{@link #publishPaymentCompleted} — gateway confirmed the charge (triggers booking confirmation)</li>
 *   <li>{@link #publishPaymentFailed}    — gateway declined the charge (triggers booking cancellation)</li>
 * </ul>
 *
 * <p>Publishing is fire-and-forget: failures are logged but never propagate to the caller.
 */
public interface PaymentEventPublisher {

    /**
     * Publishes a {@code PaymentCompletedEvent} after the gateway confirms a successful charge.
     * Consumed by booking-service to confirm the booking.
     */
    void publishPaymentCompleted(PaymentEntity payment);

    /**
     * Publishes a {@code PaymentFailedEvent} after the gateway declines or times out.
     * Consumed by booking-service to cancel the booking and release seats.
     */
    void publishPaymentFailed(PaymentEntity payment);
}
