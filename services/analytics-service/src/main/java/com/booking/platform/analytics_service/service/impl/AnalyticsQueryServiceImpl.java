package com.booking.platform.analytics_service.service.impl;

import com.booking.platform.analytics_service.config.CacheConfig;
import com.booking.platform.analytics_service.constants.AnalyticsConstants;
import com.booking.platform.analytics_service.constants.AnalyticsConstants.Booking;
import com.booking.platform.analytics_service.constants.AnalyticsConstants.Collection;
import com.booking.platform.analytics_service.constants.AnalyticsConstants.Event;
import com.booking.platform.analytics_service.constants.AnalyticsConstants.Payment;
import com.booking.platform.analytics_service.dto.response.*;
import com.booking.platform.analytics_service.service.AnalyticsQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsQueryServiceImpl implements AnalyticsQueryService {

    private final MongoTemplate mongoTemplate;

    /**
     * Top N events ranked by total revenue from {@code event_stats}.
     */
    @Override
    @Cacheable(value = CacheConfig.CACHE_EVENT_STATS, key = "'top-revenue:' + #limit")
    public List<TopRevenueEvent> getTopEventsByRevenue(int limit) {
        Aggregation aggregation = newAggregation(
                match(Criteria.where(Booking.TOTAL_REVENUE).gt(0)),
                sort(Sort.Direction.DESC, Booking.TOTAL_REVENUE),
                limit(limit),
                project(AnalyticsConstants.EVENT_ID, AnalyticsConstants.EVENT_TITLE,
                        AnalyticsConstants.CATEGORY, Booking.TOTAL_REVENUE,
                        Booking.CONFIRMED_BOOKINGS, AnalyticsConstants.CURRENCY)
        );

        return mongoTemplate
                .aggregate(aggregation, Collection.EVENT_STATS, TopRevenueEvent.class)
                .getMappedResults();
    }

    /**
     * Daily booking trends over the last N days from {@code daily_metrics}.
     *
     * <p>Uses {@code $ifNull} to default missing counters to 0 — a daily_metrics
     * document may not have booking fields if only event or payment events
     * occurred on that day.
     */
    @Override
    @Cacheable(value = CacheConfig.CACHE_DAILY_METRICS, key = "'booking-trends:' + #days")
    public List<BookingTrend> getBookingTrends(int days) {
        String startDate = LocalDate.now(ZoneOffset.UTC).minusDays(days).toString();

        Aggregation aggregation = newAggregation(
                match(Criteria.where(AnalyticsConstants.DATE).gte(startDate)),
                sort(Sort.Direction.ASC, AnalyticsConstants.DATE),
                project(AnalyticsConstants.DATE)
                        .andExpression("ifNull(bookingsCreated, 0)").as(Booking.BOOKINGS_CREATED)
                        .andExpression("ifNull(bookingsConfirmed, 0)").as(Booking.BOOKINGS_CONFIRMED)
                        .andExpression("ifNull(bookingsCancelled, 0)").as(Booking.BOOKINGS_CANCELLED)
                        .andExpression("ifNull(totalRevenue, 0)").as(Booking.TOTAL_REVENUE)
        );

        return mongoTemplate
                .aggregate(aggregation, Collection.DAILY_METRICS, BookingTrend.class)
                .getMappedResults();
    }

    /**
     * Revenue breakdown by category from {@code category_stats}.
     */
    @Override
    @Cacheable(value = CacheConfig.CACHE_CATEGORY_STATS, key = "'revenue-by-category'")
    public List<CategoryRevenue> getRevenueByCategory() {
        Aggregation aggregation = newAggregation(
                match(Criteria.where(Booking.TOTAL_REVENUE).gt(0)),
                sort(Sort.Direction.DESC, Booking.TOTAL_REVENUE),
                project(AnalyticsConstants.CATEGORY, Booking.TOTAL_REVENUE,
                        Booking.TOTAL_BOOKINGS)
        );

        return mongoTemplate
                .aggregate(aggregation, Collection.CATEGORY_STATS, CategoryRevenue.class)
                .getMappedResults();
    }

    /**
     * Platform-wide cancellation rate aggregated from {@code daily_metrics}.
     */
    @Override
    @Cacheable(value = CacheConfig.CACHE_DAILY_METRICS, key = "'cancellation-rate'")
    public CancellationRate getCancellationRate() {
        Aggregation aggregation = newAggregation(
                group()
                        .sum(Booking.BOOKINGS_CREATED).as("totalBookingsCreated")
                        .sum(Booking.BOOKINGS_CANCELLED).as("totalBookingsCancelled"),
                project("totalBookingsCreated", "totalBookingsCancelled")
                        .andExpression("cond(totalBookingsCreated == 0, 0, totalBookingsCancelled / totalBookingsCreated)")
                        .as("cancellationRate")
        );

        List<CancellationRate> results = mongoTemplate
                .aggregate(aggregation, Collection.DAILY_METRICS, CancellationRate.class)
                .getMappedResults();

        return results.isEmpty()
                ? new CancellationRate(0, 0, 0.0)
                : results.getFirst();
    }

    /**
     * Platform-wide average booking value aggregated from {@code daily_metrics}.
     */
    @Override
    @Cacheable(value = CacheConfig.CACHE_DAILY_METRICS, key = "'avg-booking-value'")
    public AverageBookingValue getAverageBookingValue() {
        Aggregation aggregation = newAggregation(
                group()
                        .sum(Booking.TOTAL_REVENUE).as(Booking.TOTAL_REVENUE)
                        .sum(Booking.BOOKINGS_CONFIRMED).as("totalConfirmedBookings"),
                project(Booking.TOTAL_REVENUE, "totalConfirmedBookings")
                        .andExpression("cond(totalConfirmedBookings == 0, 0, totalRevenue / totalConfirmedBookings)")
                        .as("averageValue")
        );

        List<AverageBookingValue> results = mongoTemplate
                .aggregate(aggregation, Collection.DAILY_METRICS, AverageBookingValue.class)
                .getMappedResults();

        return results.isEmpty()
                ? new AverageBookingValue(0.0, 0, 0.0)
                : results.getFirst();
    }

    /**
     * All events with booking activity from {@code event_stats}.
     * Used by Grafana variable dropdown for per-event drill-down.
     *
     * <p>Uses {@code $ifNull} to default missing numeric counters to 0 —
     * an event_stats document may not have refund or cancellation fields
     * if those events never occurred for that event.
     */
    @Override
    @Cacheable(value = CacheConfig.CACHE_EVENT_STATS, key = "'all-events'")
    public List<EventStatsDetail> getAllEventStats() {
        Aggregation aggregation = newAggregation(
                match(Criteria.where(Booking.TOTAL_BOOKINGS).gt(0)),
                sort(Sort.Direction.ASC, AnalyticsConstants.EVENT_TITLE),
                project(AnalyticsConstants.EVENT_ID, AnalyticsConstants.EVENT_TITLE,
                        AnalyticsConstants.CATEGORY, AnalyticsConstants.CURRENCY)
                        .andExpression("ifNull(totalBookings, 0)").as(Booking.TOTAL_BOOKINGS)
                        .andExpression("ifNull(confirmedBookings, 0)").as(Booking.CONFIRMED_BOOKINGS)
                        .andExpression("ifNull(cancelledBookings, 0)").as(Booking.CANCELLED_BOOKINGS)
                        .andExpression("ifNull(totalRevenue, 0)").as(Booking.TOTAL_REVENUE)
                        .andExpression("ifNull(totalRefunds, 0)").as(Payment.TOTAL_REFUNDS)
        );

        return mongoTemplate
                .aggregate(aggregation, Collection.EVENT_STATS, EventStatsDetail.class)
                .getMappedResults();
    }

    /**
     * Single event statistics from {@code event_stats} by eventId.
     *
     * <p>Uses {@code $ifNull} to default missing numeric counters to 0.
     */
    @Override
    @Cacheable(value = CacheConfig.CACHE_EVENT_STATS, key = "'event:' + #eventId")
    public EventStatsDetail getEventAnalytics(String eventId) {
        Aggregation aggregation = newAggregation(
                match(Criteria.where(AnalyticsConstants.EVENT_ID).is(eventId)),
                project(AnalyticsConstants.EVENT_ID, AnalyticsConstants.EVENT_TITLE,
                        AnalyticsConstants.CATEGORY, AnalyticsConstants.CURRENCY)
                        .andExpression("ifNull(totalBookings, 0)").as(Booking.TOTAL_BOOKINGS)
                        .andExpression("ifNull(confirmedBookings, 0)").as(Booking.CONFIRMED_BOOKINGS)
                        .andExpression("ifNull(cancelledBookings, 0)").as(Booking.CANCELLED_BOOKINGS)
                        .andExpression("ifNull(totalRevenue, 0)").as(Booking.TOTAL_REVENUE)
                        .andExpression("ifNull(totalRefunds, 0)").as(Payment.TOTAL_REFUNDS)
        );

        List<EventStatsDetail> results = mongoTemplate
                .aggregate(aggregation, Collection.EVENT_STATS, EventStatsDetail.class)
                .getMappedResults();

        return results.isEmpty() ? null : results.getFirst();
    }

    /**
     * Daily payment trends over the last N days from {@code daily_metrics}.
     *
     * <p>Uses {@code $ifNull} to default missing counters to 0 — a daily_metrics
     * document may not have payment fields if only booking or event lifecycle
     * events occurred on that day.
     */
    @Override
    @Cacheable(value = CacheConfig.CACHE_DAILY_METRICS, key = "'payment-trends:' + #days")
    public List<PaymentTrend> getPaymentTrends(int days) {
        String startDate = LocalDate.now(ZoneOffset.UTC).minusDays(days).toString();

        Aggregation aggregation = newAggregation(
                match(Criteria.where(AnalyticsConstants.DATE).gte(startDate)),
                sort(Sort.Direction.ASC, AnalyticsConstants.DATE),
                project(AnalyticsConstants.DATE)
                        .andExpression("ifNull(paymentsCompleted, 0)").as(Payment.PAYMENTS_COMPLETED)
                        .andExpression("ifNull(paymentsFailed, 0)").as(Payment.PAYMENTS_FAILED)
                        .andExpression("ifNull(refundsCompleted, 0)").as(Payment.REFUNDS_COMPLETED)
                        .andExpression("ifNull(totalRefunds, 0)").as(Payment.TOTAL_REFUNDS)
        );

        return mongoTemplate
                .aggregate(aggregation, Collection.DAILY_METRICS, PaymentTrend.class)
                .getMappedResults();
    }

    /**
     * Daily event lifecycle trends over the last N days from {@code daily_metrics}.
     *
     * <p>Uses {@code $ifNull} to default missing counters to 0 — a daily_metrics
     * document may not have event lifecycle fields if only booking or payment
     * events occurred on that day.
     */
    @Override
    @Cacheable(value = CacheConfig.CACHE_DAILY_METRICS, key = "'event-lifecycle:' + #days")
    public List<EventLifecycleTrend> getEventLifecycleTrends(int days) {
        String startDate = LocalDate.now(ZoneOffset.UTC).minusDays(days).toString();

        Aggregation aggregation = newAggregation(
                match(Criteria.where(AnalyticsConstants.DATE).gte(startDate)),
                sort(Sort.Direction.ASC, AnalyticsConstants.DATE),
                project(AnalyticsConstants.DATE)
                        .andExpression("ifNull(eventsCreated, 0)").as(Event.EVENTS_CREATED)
                        .andExpression("ifNull(eventsPublished, 0)").as(Event.EVENTS_PUBLISHED)
                        .andExpression("ifNull(eventsCancelled, 0)").as(Event.EVENTS_CANCELLED)
        );

        return mongoTemplate
                .aggregate(aggregation, Collection.DAILY_METRICS, EventLifecycleTrend.class)
                .getMappedResults();
    }
}
