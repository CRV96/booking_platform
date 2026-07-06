package com.booking.platform.analytics_service.service;

import com.booking.platform.analytics_service.dto.PaymentDto;

/**
 * Processes payment-domain Kafka events for analytics.
 *
 * <p>Handles: PaymentCompleted, PaymentFailed, RefundCompleted.
 * Each method saves the raw event to {@code events_log} and updates
 * the relevant materialized views ({@code daily_metrics}).
 *
 * <p>Payment events only update {@code daily_metrics} because they carry
 * {@code bookingId} but not {@code eventId} — per-event stats are handled
 * by booking events instead, keeping the service fully decoupled.
 */
public interface PaymentAnalyticsProcessor {

    void processPaymentCompleted(PaymentDto payment);

    void processPaymentFailed(PaymentDto payment);

    void processRefundCompleted(PaymentDto payment);
}
