package com.booking.platform.analytics_service.service.impl;

import com.booking.platform.analytics_service.repository.EventLogRepository;
import com.booking.platform.analytics_service.service.BookingAnalyticsProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Processes booking-domain Kafka events.
 *
 * <p>Updates:
 * <ul>
 *   <li>{@code events_log} — raw event append (all events)</li>
 *   <li>{@code event_stats} — booking/revenue counters per event</li>
 *   <li>{@code daily_metrics} — booking lifecycle counters</li>
 *   <li>{@code category_stats} — booking/revenue counters per category</li>
 * </ul>
 */
@Slf4j
@Service
public class BookingAnalyticsProcessorImpl extends BaseAnalyticsProcessor
        implements BookingAnalyticsProcessor {

    public BookingAnalyticsProcessorImpl(EventLogRepository eventLogRepository,
                                         MongoTemplate mongoTemplate) {
        super(eventLogRepository, mongoTemplate);
    }

    @Override
    public void processBookingCreated(String topic, String key,
                                      String bookingId, String eventId,
                                      double totalPrice, String currency) {
        saveRawEvent("BookingCreatedEvent", topic, key, Map.of(
                "bookingId", bookingId, "eventId", eventId,
                "totalPrice", totalPrice, "currency", currency));

        // event_stats: increment totalBookings
        upsertEventStats(eventId, new Update()
                .inc("totalBookings", 1)
                .setOnInsert("currency", currency)
                .currentDate("lastUpdated"));

        // daily_metrics: increment bookingsCreated
        upsertDailyMetrics(new Update().inc("bookingsCreated", 1));

        // category_stats: increment totalBookings (category resolved from event_stats)
        incrementCategoryStatsByEventId(eventId, new Update().inc("totalBookings", 1));

        log.debug("Processed BookingCreatedEvent: bookingId='{}', eventId='{}'", bookingId, eventId);
    }

    @Override
    public void processBookingConfirmed(String topic, String key,
                                        String bookingId, String eventId,
                                        double totalPrice, String currency,
                                        String eventTitle, String seatCategory) {
        saveRawEvent("BookingConfirmedEvent", topic, key, Map.of(
                "bookingId", bookingId, "eventId", eventId,
                "totalPrice", totalPrice, "currency", currency,
                "eventTitle", eventTitle, "seatCategory", seatCategory));

        // event_stats: increment confirmedBookings + totalRevenue
        upsertEventStats(eventId, new Update()
                .inc("confirmedBookings", 1)
                .inc("totalRevenue", totalPrice)
                .set("eventTitle", eventTitle)
                .setOnInsert("currency", currency)
                .currentDate("lastUpdated"));

        // daily_metrics: increment bookingsConfirmed + totalRevenue
        upsertDailyMetrics(new Update()
                .inc("bookingsConfirmed", 1)
                .inc("totalRevenue", totalPrice));

        // category_stats: increment totalRevenue (category resolved from event_stats)
        incrementCategoryStatsByEventId(eventId, new Update().inc("totalRevenue", totalPrice));

        log.debug("Processed BookingConfirmedEvent: bookingId='{}', eventId='{}', revenue={}",
                bookingId, eventId, totalPrice);
    }

    @Override
    public void processBookingCancelled(String topic, String key,
                                        String bookingId, String eventId) {
        saveRawEvent("BookingCancelledEvent", topic, key, Map.of(
                "bookingId", bookingId, "eventId", eventId));

        // event_stats: increment cancelledBookings
        upsertEventStats(eventId, new Update()
                .inc("cancelledBookings", 1)
                .currentDate("lastUpdated"));

        // daily_metrics: increment bookingsCancelled
        upsertDailyMetrics(new Update().inc("bookingsCancelled", 1));

        log.debug("Processed BookingCancelledEvent: bookingId='{}', eventId='{}'", bookingId, eventId);
    }
}
