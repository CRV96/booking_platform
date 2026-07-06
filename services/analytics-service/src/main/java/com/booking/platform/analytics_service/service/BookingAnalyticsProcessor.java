package com.booking.platform.analytics_service.service;

import com.booking.platform.analytics_service.dto.BookingDto;

/**
 * Processes booking-domain Kafka events for analytics.
 *
 * <p>Handles: BookingCreated, BookingConfirmed, BookingCancelled.
 * Each method saves the raw event to {@code events_log} and updates
 * the relevant materialized views ({@code event_stats}, {@code daily_metrics},
 * {@code category_stats}).
 */
public interface BookingAnalyticsProcessor {

    void processBookingCreated(BookingDto booking);

    void processBookingConfirmed(BookingDto booking);

    void processBookingCancelled(BookingDto booking);
}
