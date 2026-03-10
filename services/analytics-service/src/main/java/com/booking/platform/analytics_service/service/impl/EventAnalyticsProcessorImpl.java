package com.booking.platform.analytics_service.service.impl;

import com.booking.platform.analytics_service.constants.BkgAnalyticsConstants;
import com.booking.platform.analytics_service.constants.BkgAnalyticsConstants.BkgEventConstants;
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
        saveRawEvent(BkgEventConstants.EVENT_CREATED_EVENT, event.topic(), event.key(), Map.of(
                BkgAnalyticsConstants.PAYLOAD_EVENT_ID, event.eventId(),
                BkgEventConstants.PAYLOAD_TITLE, event.title(),
                BkgEventConstants.PAYLOAD_CATEGORY, event.category()));

        // event_stats: initialize entry for this event
        upsertEventStats(event.eventId(), new Update()
                .setOnInsert(BkgAnalyticsConstants.PAYLOAD_EVENT_ID, event.eventId())
                .setOnInsert(BkgAnalyticsConstants.PAYLOAD_EVENT_TITLE, event.title())
                .setOnInsert(BkgEventConstants.PAYLOAD_CATEGORY, event.category())
                .currentDate(BkgAnalyticsConstants.LAST_UPDATED));

        // daily_metrics: increment eventsCreated
        upsertDailyMetrics(new Update().inc(BkgEventConstants.EVENTS_CREATED, 1));

        // category_stats: increment totalEvents
        upsertCategoryStats(event.category(), new Update().inc(BkgEventConstants.TOTAL_EVENTS, 1));

        log.debug("Processed EventCreatedEvent: eventId='{}'", event.eventId());
    }

    @Override
    public void processEventUpdated(EventDto event) {
        saveRawEvent(BkgEventConstants.EVENT_UPDATED_EVENT, event.topic(), event.key(), Map.of(
                BkgAnalyticsConstants.PAYLOAD_EVENT_ID, event.eventId(),
                BkgEventConstants.PAYLOAD_CHANGED_FIELDS, event.changedFields()));

        // EventUpdated only goes to the raw log — no materialized view updates needed
        log.debug("Processed EventUpdatedEvent: eventId='{}'", event.eventId());
    }

    @Override
    public void processEventPublished(EventDto event) {
        saveRawEvent(BkgEventConstants.EVENT_PUBLISHED_EVENT, event.topic(), event.key(), Map.of(
                BkgAnalyticsConstants.PAYLOAD_EVENT_ID, event.eventId(),
                BkgEventConstants.PAYLOAD_TITLE, event.title(),
                BkgEventConstants.PAYLOAD_CATEGORY, event.category()));

        // daily_metrics: increment eventsPublished
        upsertDailyMetrics(new Update().inc(BkgEventConstants.EVENTS_PUBLISHED, 1));

        // category_stats: increment publishedEvents
        upsertCategoryStats(event.category(), new Update().inc(BkgEventConstants.PUBLISHED_EVENTS, 1));

        log.debug("Processed EventPublishedEvent: eventId='{}'", event.eventId());
    }

    @Override
    public void processEventCancelled(EventDto event) {
        saveRawEvent(BkgEventConstants.EVENT_CANCELLED_EVENT, event.topic(), event.key(), Map.of(
                BkgAnalyticsConstants.PAYLOAD_EVENT_ID, event.eventId(),
                BkgAnalyticsConstants.PAYLOAD_REASON, event.reason()));

        // daily_metrics: increment eventsCancelled
        upsertDailyMetrics(new Update().inc(BkgEventConstants.EVENTS_CANCELLED, 1));

        log.debug("Processed EventCancelledEvent: eventId='{}'", event.eventId());
    }
}
