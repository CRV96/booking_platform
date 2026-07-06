package com.booking.platform.analytics_service.service;

import com.booking.platform.analytics_service.dto.EventDto;

/**
 * Processes event-domain Kafka events for analytics.
 *
 * <p>Handles: EventCreated, EventUpdated, EventPublished, EventCancelled.
 * Each method saves the raw event to {@code events_log} and updates
 * the relevant materialized views ({@code event_stats}, {@code daily_metrics},
 * {@code category_stats}).
 */
public interface EventAnalyticsProcessor {

    void processEventCreated(EventDto event);

    void processEventUpdated(EventDto event);

    void processEventPublished(EventDto event);

    void processEventCancelled(EventDto event);
}
