package com.booking.platform.analytics_service.service.impl;

import com.booking.platform.analytics_service.constants.BkgAnalyticsConstants;
import com.booking.platform.analytics_service.constants.BkgAnalyticsConstants.BkgBookingConstants;
import com.booking.platform.analytics_service.dto.BookingDto;
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
    public void processBookingCreated(BookingDto booking) {
        saveRawEvent(BkgBookingConstants.BOOKING_CREATED_EVENT, booking.topic(), booking.key(), Map.of(
                BkgAnalyticsConstants.PAYLOAD_BOOKING_ID, booking.bookingId(),
                BkgAnalyticsConstants.PAYLOAD_EVENT_ID, booking.eventId(),
                BkgBookingConstants.PAYLOAD_TOTAL_PRICE, booking.totalPrice(),
                BkgAnalyticsConstants.PAYLOAD_CURRENCY, booking.currency()));

        // event_stats: increment totalBookings
        upsertEventStats(booking.eventId(), new Update()
                .inc(BkgBookingConstants.TOTAL_BOOKINGS, 1)
                .setOnInsert(BkgAnalyticsConstants.PAYLOAD_CURRENCY, booking.currency())
                .currentDate(BkgAnalyticsConstants.PAYLOAD_LAST_UPDATED));

        // daily_metrics: increment bookingsCreated
        upsertDailyMetrics(new Update().inc(BkgBookingConstants.BOOKINGS_CREATED, 1));

        // category_stats: increment totalBookings (category resolved from event_stats)
        incrementCategoryStatsByEventId(booking.eventId(), new Update()
                .inc(BkgBookingConstants.TOTAL_BOOKINGS, 1));

        log.debug("Processed BookingCreatedEvent: bookingId='{}', eventId='{}'",
                booking.bookingId(), booking.eventId());
    }

    @Override
    public void processBookingConfirmed(BookingDto booking) {
        saveRawEvent(BkgBookingConstants.BOOKING_CONFIRMED_EVENT, booking.topic(), booking.key(), Map.of(
                BkgAnalyticsConstants.PAYLOAD_BOOKING_ID, booking.bookingId(),
                BkgAnalyticsConstants.PAYLOAD_EVENT_ID, booking.eventId(),
                BkgBookingConstants.PAYLOAD_TOTAL_PRICE, booking.totalPrice(),
                BkgAnalyticsConstants.PAYLOAD_CURRENCY, booking.currency(),
                BkgAnalyticsConstants.PAYLOAD_EVENT_TITLE, booking.eventTitle(),
                BkgBookingConstants.PAYLOAD_SEAT_CATEGORY, booking.seatCategory()));

        // event_stats: increment confirmedBookings + totalRevenue
        upsertEventStats(booking.eventId(), new Update()
                .inc(BkgBookingConstants.CONFIRMED_BOOKINGS, 1)
                .inc(BkgBookingConstants.TOTAL_REVENUE, booking.totalPrice())
                .set(BkgAnalyticsConstants.PAYLOAD_EVENT_TITLE, booking.eventTitle())
                .setOnInsert(BkgAnalyticsConstants.PAYLOAD_CURRENCY, booking.currency())
                .currentDate(BkgAnalyticsConstants.PAYLOAD_LAST_UPDATED));

        // daily_metrics: increment bookingsConfirmed + totalRevenue
        upsertDailyMetrics(new Update()
                .inc(BkgBookingConstants.BOOKINGS_CONFIRMED, 1)
                .inc(BkgBookingConstants.TOTAL_REVENUE, booking.totalPrice()));

        // category_stats: increment totalRevenue (category resolved from event_stats)
        incrementCategoryStatsByEventId(booking.eventId(), new Update()
                .inc(BkgBookingConstants.TOTAL_REVENUE, booking.totalPrice()));

        log.debug("Processed BookingConfirmedEvent: bookingId='{}', eventId='{}', revenue={}",
                booking.bookingId(), booking.eventId(), booking.totalPrice());
    }

    @Override
    public void processBookingCancelled(BookingDto booking) {
        saveRawEvent(BkgBookingConstants.BOOKING_CANCELLED_EVENT, booking.topic(), booking.key(), Map.of(
                BkgAnalyticsConstants.PAYLOAD_BOOKING_ID, booking.bookingId(),
                BkgAnalyticsConstants.PAYLOAD_EVENT_ID, booking.eventId()));

        // event_stats: increment cancelledBookings
        upsertEventStats(booking.eventId(), new Update()
                .inc(BkgBookingConstants.CANCELLED_BOOKINGS, 1)
                .currentDate(BkgAnalyticsConstants.PAYLOAD_LAST_UPDATED));

        // daily_metrics: increment bookingsCancelled
        upsertDailyMetrics(new Update().inc(BkgBookingConstants.BOOKINGS_CANCELLED, 1));

        log.debug("Processed BookingCancelledEvent: bookingId='{}', eventId='{}'",
                booking.bookingId(), booking.eventId());
    }
}
