package com.booking.platform.analytics_service.service.impl;

import com.booking.platform.analytics_service.constants.AnalyticsConstants;
import com.booking.platform.analytics_service.constants.AnalyticsConstants.Booking;
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
        saveRawEvent(Booking.CREATED_EVENT, booking.topic(), booking.key(), Map.of(
                AnalyticsConstants.PAYLOAD_BOOKING_ID, booking.bookingId(),
                AnalyticsConstants.PAYLOAD_EVENT_ID, booking.eventId(),
                Booking.PAYLOAD_TOTAL_PRICE, booking.totalPrice(),
                AnalyticsConstants.PAYLOAD_CURRENCY, booking.currency()));

        // event_stats: increment totalBookings
        upsertEventStats(booking.eventId(), new Update()
                .inc(Booking.TOTAL_BOOKINGS, 1)
                .setOnInsert(AnalyticsConstants.PAYLOAD_CURRENCY, booking.currency())
                .currentDate(AnalyticsConstants.LAST_UPDATED));

        // daily_metrics: increment bookingsCreated
        upsertDailyMetrics(new Update().inc(Booking.BOOKINGS_CREATED, 1));

        // category_stats: increment totalBookings (category resolved from event_stats)
        incrementCategoryStatsByEventId(booking.eventId(), new Update()
                .inc(Booking.TOTAL_BOOKINGS, 1));

        log.debug("Processed BookingCreatedEvent: bookingId='{}', eventId='{}'",
                booking.bookingId(), booking.eventId());
    }

    @Override
    public void processBookingConfirmed(BookingDto booking) {
        saveRawEvent(Booking.CONFIRMED_EVENT, booking.topic(), booking.key(), Map.of(
                AnalyticsConstants.PAYLOAD_BOOKING_ID, booking.bookingId(),
                AnalyticsConstants.PAYLOAD_EVENT_ID, booking.eventId(),
                Booking.PAYLOAD_TOTAL_PRICE, booking.totalPrice(),
                AnalyticsConstants.PAYLOAD_CURRENCY, booking.currency(),
                AnalyticsConstants.PAYLOAD_EVENT_TITLE, booking.eventTitle(),
                Booking.PAYLOAD_SEAT_CATEGORY, booking.seatCategory()));

        // event_stats: increment confirmedBookings + totalRevenue
        upsertEventStats(booking.eventId(), new Update()
                .inc(Booking.CONFIRMED_BOOKINGS, 1)
                .inc(Booking.TOTAL_REVENUE, booking.totalPrice())
                .set(AnalyticsConstants.PAYLOAD_EVENT_TITLE, booking.eventTitle())
                .setOnInsert(AnalyticsConstants.PAYLOAD_CURRENCY, booking.currency())
                .currentDate(AnalyticsConstants.LAST_UPDATED));

        // daily_metrics: increment bookingsConfirmed + totalRevenue
        upsertDailyMetrics(new Update()
                .inc(Booking.BOOKINGS_CONFIRMED, 1)
                .inc(Booking.TOTAL_REVENUE, booking.totalPrice()));

        // category_stats: increment totalRevenue (category resolved from event_stats)
        incrementCategoryStatsByEventId(booking.eventId(), new Update()
                .inc(Booking.TOTAL_REVENUE, booking.totalPrice()));

        log.debug("Processed BookingConfirmedEvent: bookingId='{}', eventId='{}', revenue={}",
                booking.bookingId(), booking.eventId(), booking.totalPrice());
    }

    @Override
    public void processBookingCancelled(BookingDto booking) {
        saveRawEvent(Booking.CANCELLED_EVENT, booking.topic(), booking.key(), Map.of(
                AnalyticsConstants.PAYLOAD_BOOKING_ID, booking.bookingId(),
                AnalyticsConstants.PAYLOAD_EVENT_ID, booking.eventId(),
                AnalyticsConstants.PAYLOAD_REASON, booking.reason()));

        // event_stats: increment cancelledBookings
        upsertEventStats(booking.eventId(), new Update()
                .inc(Booking.CANCELLED_BOOKINGS, 1)
                .currentDate(AnalyticsConstants.LAST_UPDATED));

        // daily_metrics: increment bookingsCancelled
        upsertDailyMetrics(new Update().inc(Booking.BOOKINGS_CANCELLED, 1));

        log.debug("Processed BookingCancelledEvent: bookingId='{}', eventId='{}'",
                booking.bookingId(), booking.eventId());
    }
}
