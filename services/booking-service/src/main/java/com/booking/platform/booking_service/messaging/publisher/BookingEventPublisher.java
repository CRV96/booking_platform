package com.booking.platform.booking_service.messaging.publisher;

import com.booking.platform.booking_service.entity.BookingEntity;

/**
 * Publishes booking lifecycle events to Kafka.
 *
 * <p>Each method maps to a state transition in the booking lifecycle:
 * <ul>
 *   <li>{@link #publishBookingCreated}   — booking enters PENDING (triggers payment)</li>
 *   <li>{@link #publishBookingConfirmed} — payment succeeds (triggers ticket generation + email)</li>
 *   <li>{@link #publishBookingCancelled} — booking cancelled by user/system (triggers seat release + email)</li>
 * </ul>
 *
 * <p>Publishing is fire-and-forget: the caller does not wait for broker acknowledgement.
 * Failures are logged but never propagate to the business transaction.
 */
public interface BookingEventPublisher {

    /**
     * Publishes a {@code BookingCreatedEvent} after a new booking is persisted with PENDING status.
     * Consumed by payment-service to initiate the payment charge.
     */
    void publishBookingCreated(BookingEntity booking);

    /**
     * Publishes a {@code BookingConfirmedEvent} after payment succeeds and booking is CONFIRMED.
     * Consumed by ticket-service (generate tickets) and notification-service (confirmation email).
     */
    void publishBookingConfirmed(BookingEntity booking);

    /**
     * Publishes a {@code BookingCancelledEvent} after a booking is cancelled (user action or system expiration).
     * Consumed by event-service (seat release) and notification-service (cancellation email).
     */
    void publishBookingCancelled(BookingEntity booking);
}
