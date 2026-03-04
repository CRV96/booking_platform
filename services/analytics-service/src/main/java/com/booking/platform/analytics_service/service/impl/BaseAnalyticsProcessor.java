package com.booking.platform.analytics_service.service.impl;

import com.booking.platform.analytics_service.constants.BkgAnalyticsConstants;
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
    protected void saveRawEvent(String eventType, PaymentDto payment, Map<String, Object> payload) {

        EventLog eventLog = EventLog.builder()
                .eventType(eventType)
                .topic(payment.topic())
                .key(payment.key())
                .payload(payload)
                .receivedAt(Instant.now())
                .build();

        eventLogRepository.save(eventLog);
    }

    /**
     * Upserts into {@code event_stats} collection by eventId.
     * Creates the document if it doesn't exist, otherwise applies the update.
     */
    protected void upsertEventStats(String eventId, Update update) {
        Query query = Query.query(Criteria.where("eventId").is(eventId));
        mongoTemplate.upsert(query, update, "event_stats");
    }

    /**
     * Upserts into {@code daily_metrics} collection by today's UTC date.
     * Creates the document if it doesn't exist, otherwise increments counters.
     */
    protected void upsertDailyMetrics(Update update) {
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        Query query = Query.query(Criteria.where("date").is(today));
        update.setOnInsert("date", today);
        update.currentDate("lastUpdated");
        mongoTemplate.upsert(query, update, "daily_metrics");
    }

    /**
     * Upserts into {@code category_stats} collection by category name.
     * Creates the document if it doesn't exist, otherwise increments counters.
     */
    protected void upsertCategoryStats(String category, Update update) {
        Query query = Query.query(Criteria.where("category").is(category));
        update.setOnInsert("category", category);
        update.currentDate("lastUpdated");
        mongoTemplate.upsert(query, update, "category_stats");
    }

    /**
     * Looks up the category for an eventId from {@code event_stats}, then
     * updates {@code category_stats}. If the event_stats entry doesn't exist
     * yet (race condition), the update is silently skipped.
     */
    protected void incrementCategoryStatsByEventId(String eventId, Update update) {
        Query eventQuery = Query.query(Criteria.where("eventId").is(eventId));
        var eventStats = mongoTemplate.findOne(eventQuery, org.bson.Document.class, "event_stats");
        if (eventStats != null && eventStats.getString("category") != null) {
            upsertCategoryStats(eventStats.getString("category"), update);
        } else {
            log.debug("Skipping category_stats update — event_stats not yet available for eventId='{}'", eventId);
        }
    }

    protected Map<String, Object> getPayload(PaymentDto payment) {
        return Map.of(
                BkgAnalyticsConstants.PAYLOAD_PAYMENT_ID, payment.paymentId(),
                BkgAnalyticsConstants.PAYLOAD_BOOKING_ID, payment.bookingId(),
                BkgAnalyticsConstants.PAYLOAD_AMOUNT, payment.amount(),
                BkgAnalyticsConstants.PAYLOAD_CURRENCY, payment.currency(),
                BkgAnalyticsConstants.PAYLOAD_REASON, payment.reason(),
                BkgAnalyticsConstants.PAYLOAD_REFUND_ID, payment.refundId());
    }
}
