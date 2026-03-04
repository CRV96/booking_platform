package com.booking.platform.analytics_service.service;

/**
 * Processes booking-domain Kafka events for analytics.
 *
 * <p>Handles: BookingCreated, BookingConfirmed, BookingCancelled.
 * Each method saves the raw event to {@code events_log} and updates
 * the relevant materialized views ({@code event_stats}, {@code daily_metrics},
 * {@code category_stats}).
 */
public interface BookingAnalyticsProcessor {

    void processBookingCreated(String topic, String key,
                               String bookingId, String eventId,
                               double totalPrice, String currency);

    void processBookingConfirmed(String topic, String key,
                                 String bookingId, String eventId,
                                 double totalPrice, String currency,
                                 String eventTitle, String seatCategory);

    void processBookingCancelled(String topic, String key,
                                 String bookingId, String eventId);
}
