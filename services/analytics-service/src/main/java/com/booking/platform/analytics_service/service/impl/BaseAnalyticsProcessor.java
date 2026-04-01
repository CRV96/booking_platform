package com.booking.platform.analytics_service.service.impl;

import com.booking.platform.analytics_service.constants.AnalyticsConstants;
import com.booking.platform.analytics_service.document.EventLog;
import com.booking.platform.analytics_service.dto.PaymentDto;
import com.booking.platform.analytics_service.repository.EventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for all analytics processors.
 *
 * <p>Provides shared helper methods used across event, booking, and payment
 * processors:
 * <ul>
 *   <li>{@link #saveRawEvent} — appends to the immutable {@code events_log}</li>
 *   <li>{@link #upsertEventStats} — {@code $inc}/{@code $set} upsert on {@code event_stats}</li>
 *   <li>{@link #upsertDailyMetrics} — {@code $inc} upsert on {@code daily_metrics} (by today's UTC date)</li>
 *   <li>{@link #upsertCategoryStats} — {@code $inc} upsert on {@code category_stats}</li>
 *   <li>{@link #incrementCategoryStatsByEventId} — resolves category from {@code event_stats}, then upserts {@code category_stats}</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public abstract class BaseAnalyticsProcessor {

    protected final EventLogRepository eventLogRepository;
    protected final MongoTemplate mongoTemplate;

    /**
     * Saves a raw event to the immutable {@code events_log} collection.
     */
    protected void saveRawEvent(String eventType, String topic, String key,
                                Map<String, Object> payload) {
        EventLog eventLog = EventLog.builder()
                .eventType(eventType)
                .topic(topic)
                .key(key)
                .payload(payload)
                .receivedAt(Instant.now())
                .build();

        eventLogRepository.save(eventLog);
    }

    /**
     * Convenience overload that extracts topic/key from a {@link PaymentDto}.
     */
    protected void saveRawEvent(String eventType, PaymentDto payment,
                                Map<String, Object> payload) {
        saveRawEvent(eventType, payment.topic(), payment.key(), payload);
    }

    /**
     * Upserts into {@code event_stats} collection by eventId.
     * Creates the document if it doesn't exist, otherwise applies the update.
     */
    protected void upsertEventStats(String eventId, Update update) {
        Query query = Query.query(Criteria.where(AnalyticsConstants.EVENT_ID).is(eventId));

        mongoTemplate.upsert(query, update, AnalyticsConstants.Collection.EVENT_STATS);
    }

    /**
     * Upserts into {@code daily_metrics} collection by today's UTC date.
     * Creates the document if it doesn't exist, otherwise increments counters.
     */
    protected void upsertDailyMetrics(Update update) {
        String today = LocalDate.now(ZoneOffset.UTC).toString();

        Query query = Query.query(Criteria.where(AnalyticsConstants.DATE).is(today));

        update.setOnInsert(AnalyticsConstants.DATE, today);
        update.currentDate(AnalyticsConstants.LAST_UPDATED);

        mongoTemplate.upsert(query, update, AnalyticsConstants.Collection.DAILY_METRICS);
    }

    /**
     * Upserts into {@code category_stats} collection by category name.
     * Creates the document if it doesn't exist, otherwise increments counters.
     */
    protected void upsertCategoryStats(String category, Update update) {
        Query query = Query.query(Criteria.where(AnalyticsConstants.CATEGORY).is(category));

        update.setOnInsert(AnalyticsConstants.CATEGORY, category);
        update.currentDate(AnalyticsConstants.LAST_UPDATED);

        mongoTemplate.upsert(query, update, AnalyticsConstants.Collection.CATEGORY_STATS);
    }

    /**
     * Looks up the category for an eventId from {@code event_stats}, then
     * updates {@code category_stats}. If the event_stats entry doesn't exist
     * yet (race condition), the update is silently skipped.
     */
    protected void incrementCategoryStatsByEventId(String eventId, Update update) {
        Query eventQuery = Query.query(Criteria.where(AnalyticsConstants.EVENT_ID).is(eventId));
        var eventStats = mongoTemplate.findOne(eventQuery, org.bson.Document.class, AnalyticsConstants.Collection.EVENT_STATS);

        if (eventStats != null && eventStats.getString(AnalyticsConstants.CATEGORY) != null) {
            upsertCategoryStats(eventStats.getString(AnalyticsConstants.CATEGORY), update);
        } else {
            log.debug("Skipping category_stats update — event_stats not yet available for eventId='{}'", eventId);
        }
    }

    // Helper method to build the payload map for a PaymentDto, used when saving raw events.
    protected Map<String, Object> getPayload(PaymentDto payment) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(AnalyticsConstants.PAYLOAD_PAYMENT_ID, payment.paymentId());
        payload.put(AnalyticsConstants.PAYLOAD_BOOKING_ID, payment.bookingId());
        payload.put(AnalyticsConstants.PAYLOAD_AMOUNT,     payment.amount());
        payload.put(AnalyticsConstants.PAYLOAD_CURRENCY,   payment.currency());
        if (payment.reason()   != null) payload.put(AnalyticsConstants.PAYLOAD_REASON,    payment.reason());
        if (payment.refundId() != null) payload.put(AnalyticsConstants.PAYLOAD_REFUND_ID, payment.refundId());
        return payload;
    }
}
