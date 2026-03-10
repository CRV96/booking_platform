package com.booking.platform.analytics_service.service.impl;

import com.booking.platform.analytics_service.config.CacheConfig;
import com.booking.platform.analytics_service.constants.BkgAnalyticsConstants;
import com.booking.platform.analytics_service.constants.BkgAnalyticsConstants.BkgBookingConstants;
import com.booking.platform.analytics_service.constants.BkgAnalyticsConstants.BkgDocumentConstants;
import com.booking.platform.analytics_service.constants.BkgAnalyticsConstants.BkgEventConstants;
import com.booking.platform.analytics_service.constants.BkgAnalyticsConstants.BkgPaymentConstants;
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
                match(Criteria.where(BkgBookingConstants.TOTAL_REVENUE).gt(0)),
                sort(Sort.Direction.DESC, BkgBookingConstants.TOTAL_REVENUE),
                limit(limit),
                project(BkgAnalyticsConstants.EVENT_ID, BkgAnalyticsConstants.EVENT_TITLE,
                        BkgAnalyticsConstants.CATEGORY, BkgBookingConstants.TOTAL_REVENUE,
                        BkgBookingConstants.CONFIRMED_BOOKINGS, BkgAnalyticsConstants.CURRENCY)
        );

        return mongoTemplate
                .aggregate(aggregation, BkgDocumentConstants.EVENT_STATS_COLLECTION, TopRevenueEvent.class)
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
                match(Criteria.where(BkgAnalyticsConstants.DATE).gte(startDate)),
                sort(Sort.Direction.ASC, BkgAnalyticsConstants.DATE),
                project(BkgAnalyticsConstants.DATE)
                        .andExpression("ifNull(" + BkgBookingConstants.BOOKINGS_CREATED + ", 0)").as(BkgBookingConstants.BOOKINGS_CREATED)
                        .andExpression("ifNull(" + BkgBookingConstants.BOOKINGS_CONFIRMED + ", 0)").as(BkgBookingConstants.BOOKINGS_CONFIRMED)
                        .andExpression("ifNull(" + BkgBookingConstants.BOOKINGS_CANCELLED + ", 0)").as(BkgBookingConstants.BOOKINGS_CANCELLED)
                        .andExpression("ifNull(" + BkgBookingConstants.TOTAL_REVENUE + ", 0)").as(BkgBookingConstants.TOTAL_REVENUE)
        );

        return mongoTemplate
                .aggregate(aggregation, BkgDocumentConstants.DAILY_METRICS_COLLECTION, BookingTrend.class)
                .getMappedResults();
    }

    /**
     * Revenue breakdown by category from {@code category_stats}.
     */
    @Override
    @Cacheable(value = CacheConfig.CACHE_CATEGORY_STATS, key = "'revenue-by-category'")
    public List<CategoryRevenue> getRevenueByCategory() {
        Aggregation aggregation = newAggregation(
                match(Criteria.where(BkgBookingConstants.TOTAL_REVENUE).gt(0)),
                sort(Sort.Direction.DESC, BkgBookingConstants.TOTAL_REVENUE),
                project(BkgAnalyticsConstants.CATEGORY, BkgBookingConstants.TOTAL_REVENUE,
                        BkgBookingConstants.TOTAL_BOOKINGS)
        );

        return mongoTemplate
                .aggregate(aggregation, BkgDocumentConstants.CATEGORY_STATS_COLLECTION, CategoryRevenue.class)
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
                        .sum(BkgBookingConstants.BOOKINGS_CREATED).as("totalBookingsCreated")
                        .sum(BkgBookingConstants.BOOKINGS_CANCELLED).as("totalBookingsCancelled"),
                project("totalBookingsCreated", "totalBookingsCancelled")
                        .andExpression(
                                "cond(totalBookingsCreated == 0, 0, " +
                                "totalBookingsCancelled / totalBookingsCreated)")
                        .as("cancellationRate")
        );

        List<CancellationRate> results = mongoTemplate
                .aggregate(aggregation, BkgDocumentConstants.DAILY_METRICS_COLLECTION, CancellationRate.class)
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
                        .sum(BkgBookingConstants.TOTAL_REVENUE).as("totalRevenue")
                        .sum(BkgBookingConstants.BOOKINGS_CONFIRMED).as("totalConfirmedBookings"),
                project("totalRevenue", "totalConfirmedBookings")
                        .andExpression(
                                "cond(totalConfirmedBookings == 0, 0, " +
                                "totalRevenue / totalConfirmedBookings)")
                        .as("averageValue")
        );

        List<AverageBookingValue> results = mongoTemplate
                .aggregate(aggregation, BkgDocumentConstants.DAILY_METRICS_COLLECTION, AverageBookingValue.class)
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
                match(Criteria.where(BkgBookingConstants.TOTAL_BOOKINGS).gt(0)),
                sort(Sort.Direction.ASC, BkgAnalyticsConstants.EVENT_TITLE),
                project(BkgAnalyticsConstants.EVENT_ID, BkgAnalyticsConstants.EVENT_TITLE,
                        BkgAnalyticsConstants.CATEGORY, BkgAnalyticsConstants.CURRENCY)
                        .andExpression("ifNull(" + BkgBookingConstants.TOTAL_BOOKINGS + ", 0)").as(BkgBookingConstants.TOTAL_BOOKINGS)
                        .andExpression("ifNull(" + BkgBookingConstants.CONFIRMED_BOOKINGS + ", 0)").as(BkgBookingConstants.CONFIRMED_BOOKINGS)
                        .andExpression("ifNull(" + BkgBookingConstants.CANCELLED_BOOKINGS + ", 0)").as(BkgBookingConstants.CANCELLED_BOOKINGS)
                        .andExpression("ifNull(" + BkgBookingConstants.TOTAL_REVENUE + ", 0)").as(BkgBookingConstants.TOTAL_REVENUE)
                        .andExpression("ifNull(" + BkgPaymentConstants.PAYMENT_REFUND_TOTAL_REFUNDS + ", 0)").as(BkgPaymentConstants.PAYMENT_REFUND_TOTAL_REFUNDS)
        );

        return mongoTemplate
                .aggregate(aggregation, BkgDocumentConstants.EVENT_STATS_COLLECTION, EventStatsDetail.class)
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
                match(Criteria.where(BkgAnalyticsConstants.EVENT_ID).is(eventId)),
                project(BkgAnalyticsConstants.EVENT_ID, BkgAnalyticsConstants.EVENT_TITLE,
                        BkgAnalyticsConstants.CATEGORY, BkgAnalyticsConstants.CURRENCY)
                        .andExpression("ifNull(" + BkgBookingConstants.TOTAL_BOOKINGS + ", 0)").as(BkgBookingConstants.TOTAL_BOOKINGS)
                        .andExpression("ifNull(" + BkgBookingConstants.CONFIRMED_BOOKINGS + ", 0)").as(BkgBookingConstants.CONFIRMED_BOOKINGS)
                        .andExpression("ifNull(" + BkgBookingConstants.CANCELLED_BOOKINGS + ", 0)").as(BkgBookingConstants.CANCELLED_BOOKINGS)
                        .andExpression("ifNull(" + BkgBookingConstants.TOTAL_REVENUE + ", 0)").as(BkgBookingConstants.TOTAL_REVENUE)
                        .andExpression("ifNull(" + BkgPaymentConstants.PAYMENT_REFUND_TOTAL_REFUNDS + ", 0)").as(BkgPaymentConstants.PAYMENT_REFUND_TOTAL_REFUNDS)
        );

        List<EventStatsDetail> results = mongoTemplate
                .aggregate(aggregation, BkgDocumentConstants.EVENT_STATS_COLLECTION, EventStatsDetail.class)
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
                match(Criteria.where(BkgAnalyticsConstants.DATE).gte(startDate)),
                sort(Sort.Direction.ASC, BkgAnalyticsConstants.DATE),
                project(BkgAnalyticsConstants.DATE)
                        .andExpression("ifNull(" + BkgPaymentConstants.PAYMENTS_COMPLETED + ", 0)").as(BkgPaymentConstants.PAYMENTS_COMPLETED)
                        .andExpression("ifNull(" + BkgPaymentConstants.PAYMENTS_FAILED + ", 0)").as(BkgPaymentConstants.PAYMENTS_FAILED)
                        .andExpression("ifNull(" + BkgPaymentConstants.PAYMENT_REFUND_COMPLETED + ", 0)").as(BkgPaymentConstants.PAYMENT_REFUND_COMPLETED)
                        .andExpression("ifNull(" + BkgPaymentConstants.PAYMENT_REFUND_TOTAL_REFUNDS + ", 0)").as(BkgPaymentConstants.PAYMENT_REFUND_TOTAL_REFUNDS)
        );

        return mongoTemplate
                .aggregate(aggregation, BkgDocumentConstants.DAILY_METRICS_COLLECTION, PaymentTrend.class)
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
                match(Criteria.where(BkgAnalyticsConstants.DATE).gte(startDate)),
                sort(Sort.Direction.ASC, BkgAnalyticsConstants.DATE),
                project(BkgAnalyticsConstants.DATE)
                        .andExpression("ifNull(" + BkgEventConstants.EVENTS_CREATED + ", 0)").as(BkgEventConstants.EVENTS_CREATED)
                        .andExpression("ifNull(" + BkgEventConstants.EVENTS_PUBLISHED + ", 0)").as(BkgEventConstants.EVENTS_PUBLISHED)
                        .andExpression("ifNull(" + BkgEventConstants.EVENTS_CANCELLED + ", 0)").as(BkgEventConstants.EVENTS_CANCELLED)
        );

        return mongoTemplate
                .aggregate(aggregation, BkgDocumentConstants.DAILY_METRICS_COLLECTION, EventLifecycleTrend.class)
                .getMappedResults();
    }
}
