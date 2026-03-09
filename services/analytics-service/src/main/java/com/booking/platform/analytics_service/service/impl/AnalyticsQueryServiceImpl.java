package com.booking.platform.analytics_service.service.impl;

import com.booking.platform.analytics_service.config.CacheConfig;
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
                match(Criteria.where("totalRevenue").gt(0)),
                sort(Sort.Direction.DESC, "totalRevenue"),
                limit(limit),
                project("eventId", "eventTitle", "category",
                        "totalRevenue", "confirmedBookings", "currency")
        );

        return mongoTemplate
                .aggregate(aggregation, "event_stats", TopRevenueEvent.class)
                .getMappedResults();
    }

    /**
     * Daily booking trends over the last N days from {@code daily_metrics}.
     */
    @Override
    @Cacheable(value = CacheConfig.CACHE_DAILY_METRICS, key = "'booking-trends:' + #days")
    public List<BookingTrend> getBookingTrends(int days) {
        String startDate = LocalDate.now(ZoneOffset.UTC).minusDays(days).toString();

        Aggregation aggregation = newAggregation(
                match(Criteria.where("date").gte(startDate)),
                sort(Sort.Direction.ASC, "date"),
                project("date", "bookingsCreated", "bookingsConfirmed",
                        "bookingsCancelled", "totalRevenue")
        );

        return mongoTemplate
                .aggregate(aggregation, "daily_metrics", BookingTrend.class)
                .getMappedResults();
    }

    /**
     * Revenue breakdown by category from {@code category_stats}.
     */
    @Override
    @Cacheable(value = CacheConfig.CACHE_CATEGORY_STATS, key = "'revenue-by-category'")
    public List<CategoryRevenue> getRevenueByCategory() {
        Aggregation aggregation = newAggregation(
                match(Criteria.where("totalRevenue").gt(0)),
                sort(Sort.Direction.DESC, "totalRevenue"),
                project("category", "totalRevenue", "totalBookings")
        );

        return mongoTemplate
                .aggregate(aggregation, "category_stats", CategoryRevenue.class)
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
                        .sum("bookingsCreated").as("totalBookingsCreated")
                        .sum("bookingsCancelled").as("totalBookingsCancelled"),
                project("totalBookingsCreated", "totalBookingsCancelled")
                        .andExpression(
                                "cond(totalBookingsCreated == 0, 0, " +
                                "totalBookingsCancelled / totalBookingsCreated)")
                        .as("cancellationRate")
        );

        List<CancellationRate> results = mongoTemplate
                .aggregate(aggregation, "daily_metrics", CancellationRate.class)
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
                        .sum("totalRevenue").as("totalRevenue")
                        .sum("bookingsConfirmed").as("totalConfirmedBookings"),
                project("totalRevenue", "totalConfirmedBookings")
                        .andExpression(
                                "cond(totalConfirmedBookings == 0, 0, " +
                                "totalRevenue / totalConfirmedBookings)")
                        .as("averageValue")
        );

        List<AverageBookingValue> results = mongoTemplate
                .aggregate(aggregation, "daily_metrics", AverageBookingValue.class)
                .getMappedResults();

        return results.isEmpty()
                ? new AverageBookingValue(0.0, 0, 0.0)
                : results.getFirst();
    }
}
