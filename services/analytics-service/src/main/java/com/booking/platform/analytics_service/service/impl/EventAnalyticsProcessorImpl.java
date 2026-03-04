package com.booking.platform.analytics_service.service.impl;

import com.booking.platform.analytics_service.repository.EventLogRepository;
import com.booking.platform.analytics_service.service.EventAnalyticsProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Processes event-domain Kafka events.
 *
 * <p>Updates:
 * <ul>
 *   <li>{@code events_log} — raw event append (all events)</li>
 *   <li>{@code event_stats} — initializes per-event document on EventCreated</li>
 *   <li>{@code daily_metrics} — increments event lifecycle counters</li>
 *   <li>{@code category_stats} — increments per-category counters</li>
 * </ul>
 */
@Slf4j
@Service
public class EventAnalyticsProcessorImpl extends BaseAnalyticsProcessor
        implements EventAnalyticsProcessor {

    public EventAnalyticsProcessorImpl(EventLogRepository eventLogRepository,
                                       MongoTemplate mongoTemplate) {
        super(eventLogRepository, mongoTemplate);
    }

    @Override
    public void processEventCreated(String topic, String key,
                                    String eventId, String title, String category) {
        saveRawEvent("EventCreatedEvent", topic, key, Map.of(
                "eventId", eventId, "title", title, "category", category));

        // event_stats: initialize entry for this event
        upsertEventStats(eventId, new Update()
                .setOnInsert("eventId", eventId)
                .setOnInsert("eventTitle", title)
                .setOnInsert("category", category)
                .currentDate("lastUpdated"));

        // daily_metrics: increment eventsCreated
        upsertDailyMetrics(new Update().inc("eventsCreated", 1));

        // category_stats: increment totalEvents
        upsertCategoryStats(category, new Update().inc("totalEvents", 1));

        log.debug("Processed EventCreatedEvent: eventId='{}'", eventId);
    }

    @Override
    public void processEventUpdated(String topic, String key,
                                    String eventId, List<String> changedFields) {
        saveRawEvent("EventUpdatedEvent", topic, key, Map.of(
                "eventId", eventId, "changedFields", changedFields));

        // EventUpdated only goes to the raw log — no materialized view updates needed
        log.debug("Processed EventUpdatedEvent: eventId='{}'", eventId);
    }

    @Override
    public void processEventPublished(String topic, String key,
                                      String eventId, String title, String category) {
        saveRawEvent("EventPublishedEvent", topic, key, Map.of(
                "eventId", eventId, "title", title, "category", category));

        // daily_metrics: increment eventsPublished
        upsertDailyMetrics(new Update().inc("eventsPublished", 1));

        // category_stats: increment publishedEvents
        upsertCategoryStats(category, new Update().inc("publishedEvents", 1));

        log.debug("Processed EventPublishedEvent: eventId='{}'", eventId);
    }

    @Override
    public void processEventCancelled(String topic, String key,
                                      String eventId, String reason) {
        saveRawEvent("EventCancelledEvent", topic, key, Map.of(
                "eventId", eventId, "reason", reason));

        // daily_metrics: increment eventsCancelled
        upsertDailyMetrics(new Update().inc("eventsCancelled", 1));

        log.debug("Processed EventCancelledEvent: eventId='{}'", eventId);
    }
}
