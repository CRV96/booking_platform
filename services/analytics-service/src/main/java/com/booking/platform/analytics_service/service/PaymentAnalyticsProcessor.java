package com.booking.platform.analytics_service.service;

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

    void processPaymentCompleted(String topic, String key,
                                 String paymentId, String bookingId,
                                 double amount, String currency);

    void processPaymentFailed(String topic, String key,
                              String paymentId, String bookingId, String reason);

    void processRefundCompleted(String topic, String key,
                                String paymentId, String bookingId,
                                String refundId, double amount, String currency);
}
