package com.booking.platform.analytics_service.service;

import java.util.List;

/**
 * Processes event-domain Kafka events for analytics.
 *
 * <p>Handles: EventCreated, EventUpdated, EventPublished, EventCancelled.
 * Each method saves the raw event to {@code events_log} and updates
 * the relevant materialized views ({@code event_stats}, {@code daily_metrics},
 * {@code category_stats}).
 */
public interface EventAnalyticsProcessor {

    void processEventCreated(String topic, String key,
                             String eventId, String title, String category);

    void processEventUpdated(String topic, String key,
                             String eventId, List<String> changedFields);

    void processEventPublished(String topic, String key,
                               String eventId, String title, String category);

    void processEventCancelled(String topic, String key,
                               String eventId, String reason);
}
