package com.booking.platform.analytics_service.service.impl;

import com.booking.platform.analytics_service.constants.AnalyticsConstants;
import com.booking.platform.analytics_service.constants.AnalyticsConstants.Event;
import com.booking.platform.analytics_service.dto.EventDto;
import com.booking.platform.analytics_service.repository.EventLogRepository;
import com.booking.platform.analytics_service.service.EventAnalyticsProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

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
    public void processEventCreated(EventDto event) {
        saveRawEvent(Event.CREATED_EVENT, event.topic(), event.key(), Map.of(
                AnalyticsConstants.PAYLOAD_EVENT_ID, event.eventId(),
                Event.PAYLOAD_TITLE, event.title(),
                Event.PAYLOAD_CATEGORY, event.category()));

        // event_stats: initialize entry for this event
        upsertEventStats(event.eventId(), new Update()
                .setOnInsert(AnalyticsConstants.PAYLOAD_EVENT_ID, event.eventId())
                .setOnInsert(AnalyticsConstants.PAYLOAD_EVENT_TITLE, event.title())
                .setOnInsert(Event.PAYLOAD_CATEGORY, event.category())
                .currentDate(AnalyticsConstants.LAST_UPDATED));

        // daily_metrics: increment eventsCreated
        upsertDailyMetrics(new Update().inc(Event.EVENTS_CREATED, 1));

        // category_stats: increment totalEvents
        upsertCategoryStats(event.category(), new Update().inc(Event.TOTAL_EVENTS, 1));

        log.debug("Processed EventCreatedEvent: eventId='{}'", event.eventId());
    }

    @Override
    public void processEventUpdated(EventDto event) {
        saveRawEvent(Event.UPDATED_EVENT, event.topic(), event.key(), Map.of(
                AnalyticsConstants.PAYLOAD_EVENT_ID, event.eventId(),
                Event.PAYLOAD_CHANGED_FIELDS, event.changedFields()));

        // EventUpdated only goes to the raw log — no materialized view updates needed
        log.debug("Processed EventUpdatedEvent: eventId='{}'", event.eventId());
    }

    @Override
    public void processEventPublished(EventDto event) {
        saveRawEvent(Event.PUBLISHED_EVENT, event.topic(), event.key(), Map.of(
                AnalyticsConstants.PAYLOAD_EVENT_ID, event.eventId(),
                Event.PAYLOAD_TITLE, event.title(),
                Event.PAYLOAD_CATEGORY, event.category()));

        // daily_metrics: increment eventsPublished
        upsertDailyMetrics(new Update().inc(Event.EVENTS_PUBLISHED, 1));

        // category_stats: increment publishedEvents
        upsertCategoryStats(event.category(), new Update().inc(Event.PUBLISHED_EVENTS, 1));

        log.debug("Processed EventPublishedEvent: eventId='{}'", event.eventId());
    }

    @Override
    public void processEventCancelled(EventDto event) {
        saveRawEvent(Event.CANCELLED_EVENT, event.topic(), event.key(), Map.of(
                AnalyticsConstants.PAYLOAD_EVENT_ID, event.eventId(),
                AnalyticsConstants.PAYLOAD_REASON, event.reason()));

        // daily_metrics: increment eventsCancelled
        upsertDailyMetrics(new Update().inc(Event.EVENTS_CANCELLED, 1));

        log.debug("Processed EventCancelledEvent: eventId='{}'", event.eventId());
    }
}
