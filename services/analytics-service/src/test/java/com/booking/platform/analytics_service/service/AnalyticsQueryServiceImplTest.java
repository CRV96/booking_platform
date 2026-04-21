package com.booking.platform.analytics_service.service;

import com.booking.platform.analytics_service.constants.AnalyticsConstants.Collection;
import com.booking.platform.analytics_service.dto.response.*;
import com.booking.platform.analytics_service.service.impl.AnalyticsQueryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsQueryServiceImplTest {

    @Mock private MongoTemplate mongoTemplate;

    @InjectMocks private AnalyticsQueryServiceImpl queryService;

    @SuppressWarnings("unchecked")
    private <T> AggregationResults<T> results(List<T> items) {
        AggregationResults<T> aggResults = mock(AggregationResults.class);
        when(aggResults.getMappedResults()).thenReturn(items);
        return aggResults;
    }

    // ── getTopEventsByRevenue ─────────────────────────────────────────────────

    @Test
    void getTopEventsByRevenue_delegatesToEventStats() {
        TopRevenueEvent item = new TopRevenueEvent("ev-1", "Rock Fest", "CONCERT", 500.0, 10, "USD");
        AggregationResults<TopRevenueEvent> aggResults = results(List.of(item));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Collection.EVENT_STATS), eq(TopRevenueEvent.class)))
                .thenReturn(aggResults);

        List<TopRevenueEvent> result = queryService.getTopEventsByRevenue(5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).eventId()).isEqualTo("ev-1");
        verify(mongoTemplate).aggregate(any(Aggregation.class), eq(Collection.EVENT_STATS), eq(TopRevenueEvent.class));
    }

    @Test
    void getTopEventsByRevenue_emptyResults_returnsEmptyList() {
        AggregationResults<TopRevenueEvent> aggResults = results(List.of());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Collection.EVENT_STATS), eq(TopRevenueEvent.class)))
                .thenReturn(aggResults);

        assertThat(queryService.getTopEventsByRevenue(10)).isEmpty();
    }

    // ── getBookingTrends ──────────────────────────────────────────────────────

    @Test
    void getBookingTrends_delegatesToDailyMetrics() {
        BookingTrend trend = new BookingTrend("2025-06-01", 5, 4, 1, 400.0);
        AggregationResults<BookingTrend> aggResults = results(List.of(trend));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Collection.DAILY_METRICS), eq(BookingTrend.class)))
                .thenReturn(aggResults);

        List<BookingTrend> result = queryService.getBookingTrends(7);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo("2025-06-01");
        verify(mongoTemplate).aggregate(any(Aggregation.class), eq(Collection.DAILY_METRICS), eq(BookingTrend.class));
    }

    @Test
    void getBookingTrends_emptyResults_returnsEmptyList() {
        AggregationResults<BookingTrend> aggResults = results(List.of());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Collection.DAILY_METRICS), eq(BookingTrend.class)))
                .thenReturn(aggResults);

        assertThat(queryService.getBookingTrends(30)).isEmpty();
    }

    // ── getRevenueByCategory ──────────────────────────────────────────────────

    @Test
    void getRevenueByCategory_delegatesToCategoryStats() {
        CategoryRevenue item = new CategoryRevenue("CONCERT", 1200.0, 20);
        AggregationResults<CategoryRevenue> aggResults = results(List.of(item));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Collection.CATEGORY_STATS), eq(CategoryRevenue.class)))
                .thenReturn(aggResults);

        List<CategoryRevenue> result = queryService.getRevenueByCategory();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).category()).isEqualTo("CONCERT");
        verify(mongoTemplate).aggregate(any(Aggregation.class), eq(Collection.CATEGORY_STATS), eq(CategoryRevenue.class));
    }

    // ── getCancellationRate ───────────────────────────────────────────────────

    @Test
    void getCancellationRate_noData_returnsZeroDefaults() {
        AggregationResults<CancellationRate> aggResults = results(List.of());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Collection.DAILY_METRICS), eq(CancellationRate.class)))
                .thenReturn(aggResults);

        CancellationRate result = queryService.getCancellationRate();

        assertThat(result.totalBookingsCreated()).isEqualTo(0);
        assertThat(result.totalBookingsCancelled()).isEqualTo(0);
        assertThat(result.cancellationRate()).isEqualTo(0.0);
    }

    @Test
    void getCancellationRate_withData_returnsFirstResult() {
        CancellationRate rate = new CancellationRate(100, 20, 0.2);
        AggregationResults<CancellationRate> aggResults = results(List.of(rate));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Collection.DAILY_METRICS), eq(CancellationRate.class)))
                .thenReturn(aggResults);

        CancellationRate result = queryService.getCancellationRate();

        assertThat(result.cancellationRate()).isEqualTo(0.2);
        assertThat(result.totalBookingsCreated()).isEqualTo(100);
    }

    // ── getAverageBookingValue ────────────────────────────────────────────────

    @Test
    void getAverageBookingValue_noData_returnsZeroDefaults() {
        AggregationResults<AverageBookingValue> aggResults = results(List.of());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Collection.DAILY_METRICS), eq(AverageBookingValue.class)))
                .thenReturn(aggResults);

        AverageBookingValue result = queryService.getAverageBookingValue();

        assertThat(result.totalRevenue()).isEqualTo(0.0);
        assertThat(result.totalConfirmedBookings()).isEqualTo(0);
        assertThat(result.averageValue()).isEqualTo(0.0);
    }

    @Test
    void getAverageBookingValue_withData_returnsFirstResult() {
        AverageBookingValue avg = new AverageBookingValue(500.0, 10, 50.0);
        AggregationResults<AverageBookingValue> aggResults = results(List.of(avg));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Collection.DAILY_METRICS), eq(AverageBookingValue.class)))
                .thenReturn(aggResults);

        AverageBookingValue result = queryService.getAverageBookingValue();

        assertThat(result.averageValue()).isEqualTo(50.0);
    }

    // ── getAllEventStats ──────────────────────────────────────────────────────

    @Test
    void getAllEventStats_delegatesToEventStats() {
        EventStatsDetail detail = new EventStatsDetail("ev-1", "Fest", "CONCERT", 10, 8, 2, 800.0, 0.0, "USD");
        AggregationResults<EventStatsDetail> aggResults = results(List.of(detail));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Collection.EVENT_STATS), eq(EventStatsDetail.class)))
                .thenReturn(aggResults);

        List<EventStatsDetail> result = queryService.getAllEventStats();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).eventId()).isEqualTo("ev-1");
    }

    @Test
    void getAllEventStats_emptyResults_returnsEmptyList() {
        AggregationResults<EventStatsDetail> aggResults = results(List.of());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Collection.EVENT_STATS), eq(EventStatsDetail.class)))
                .thenReturn(aggResults);

        assertThat(queryService.getAllEventStats()).isEmpty();
    }

    // ── getEventAnalytics ─────────────────────────────────────────────────────

    @Test
    void getEventAnalytics_found_returnsFirstResult() {
        EventStatsDetail detail = new EventStatsDetail("ev-1", "Fest", "CONCERT", 5, 4, 1, 400.0, 0.0, "USD");
        AggregationResults<EventStatsDetail> aggResults = results(List.of(detail));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Collection.EVENT_STATS), eq(EventStatsDetail.class)))
                .thenReturn(aggResults);

        EventStatsDetail result = queryService.getEventAnalytics("ev-1");

        assertThat(result).isNotNull();
        assertThat(result.eventId()).isEqualTo("ev-1");
    }

    @Test
    void getEventAnalytics_notFound_returnsNull() {
        AggregationResults<EventStatsDetail> aggResults = results(List.of());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Collection.EVENT_STATS), eq(EventStatsDetail.class)))
                .thenReturn(aggResults);

        assertThat(queryService.getEventAnalytics("ev-missing")).isNull();
    }

    // ── getPaymentTrends ──────────────────────────────────────────────────────

    @Test
    void getPaymentTrends_delegatesToDailyMetrics() {
        PaymentTrend trend = new PaymentTrend("2025-06-01", 10, 2, 1, 50.0);
        AggregationResults<PaymentTrend> aggResults = results(List.of(trend));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Collection.DAILY_METRICS), eq(PaymentTrend.class)))
                .thenReturn(aggResults);

        List<PaymentTrend> result = queryService.getPaymentTrends(14);

        assertThat(result).hasSize(1);
        verify(mongoTemplate).aggregate(any(Aggregation.class), eq(Collection.DAILY_METRICS), eq(PaymentTrend.class));
    }

    // ── getEventLifecycleTrends ───────────────────────────────────────────────

    @Test
    void getEventLifecycleTrends_delegatesToDailyMetrics() {
        EventLifecycleTrend trend = new EventLifecycleTrend("2025-06-01", 3, 2, 1);
        AggregationResults<EventLifecycleTrend> aggResults = results(List.of(trend));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Collection.DAILY_METRICS), eq(EventLifecycleTrend.class)))
                .thenReturn(aggResults);

        List<EventLifecycleTrend> result = queryService.getEventLifecycleTrends(30);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).eventsCreated()).isEqualTo(3);
    }
}
