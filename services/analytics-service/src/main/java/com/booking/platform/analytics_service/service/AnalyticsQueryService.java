package com.booking.platform.analytics_service.service;

import com.booking.platform.analytics_service.dto.response.*;

import java.util.List;

/**
 * Read-side analytics queries backed by MongoDB aggregation pipelines.
 *
 * <p>All methods are cached with a 5-minute TTL via Redis.
 */
public interface AnalyticsQueryService {

    List<TopRevenueEvent> getTopEventsByRevenue(int limit);

    List<BookingTrend> getBookingTrends(int days);

    List<CategoryRevenue> getRevenueByCategory();

    CancellationRate getCancellationRate();

    AverageBookingValue getAverageBookingValue();

    List<EventStatsDetail> getAllEventStats();

    EventStatsDetail getEventAnalytics(String eventId);

    List<PaymentTrend> getPaymentTrends(int days);

    List<EventLifecycleTrend> getEventLifecycleTrends(int days);
}
