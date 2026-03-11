package com.booking.platform.analytics_service.controller;

import com.booking.platform.analytics_service.constants.BkgAnalyticsConstants.Api;
import com.booking.platform.analytics_service.dto.response.*;
import com.booking.platform.analytics_service.service.AnalyticsQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(Api.ANALYTICS_BASE_PATH)
@RequiredArgsConstructor
@Log4j2
public class AnalyticsController {

    private final AnalyticsQueryService analyticsQueryService;

    @GetMapping(Api.TOP_REVENUE_PATH)
    public List<TopRevenueEvent> getTopEventsByRevenue(
            @RequestParam(name = Api.PARAM_LIMIT,
                    defaultValue = Api.DEFAULT_LIMIT) int limit)
    {
            final List<TopRevenueEvent> topRevenueEventList = analyticsQueryService.getTopEventsByRevenue(limit);
            log.debug("Returning top {} events by revenue: {}", limit, topRevenueEventList);

            return topRevenueEventList;
    }

    @GetMapping(Api.BOOKING_TRENDS_PATH)
    public List<BookingTrend> getBookingTrends(
            @RequestParam(name = Api.PARAM_DAYS,
                    defaultValue = Api.DEFAULT_DAYS) int days)
    {
        final List<BookingTrend> bookingTrendList = analyticsQueryService.getBookingTrends(days);
        log.debug("Returning booking trends for the past {} days: {}", days, bookingTrendList);

        return bookingTrendList;
    }

    @GetMapping(Api.REVENUE_BY_CATEGORY_PATH)
    public List<CategoryRevenue> getRevenueByCategory() {
        final List<CategoryRevenue> categoryRevenueList = analyticsQueryService.getRevenueByCategory();
        log.debug("Returning revenue by category: {}", categoryRevenueList);

        return categoryRevenueList;
    }

    @GetMapping(Api.CANCELLATION_RATE_PATH)
    public CancellationRate getCancellationRate() {
        final CancellationRate cancellationRate = analyticsQueryService.getCancellationRate();
        log.debug("Returning cancellation rate: {}", cancellationRate);

        return cancellationRate;
    }

    @GetMapping(Api.AVG_BOOKING_VALUE_PATH)
    public AverageBookingValue getAverageBookingValue() {
        final AverageBookingValue averageBookingValue = analyticsQueryService.getAverageBookingValue();
        log.debug("Returning average booking value: {}", averageBookingValue);

        return averageBookingValue;
    }

    @GetMapping(Api.ALL_EVENTS_PATH)
    public List<EventStatsDetail> getAllEventStats() {
        final List<EventStatsDetail> eventStatsList = analyticsQueryService.getAllEventStats();
        log.debug("Returning all event stats ({} events)", eventStatsList.size());

        return eventStatsList;
    }

    @GetMapping(Api.EVENT_ANALYTICS_PATH)
    public EventStatsDetail getEventAnalytics(
            @PathVariable(Api.PARAM_EVENT_ID) String eventId)
    {
        final EventStatsDetail eventStats = analyticsQueryService.getEventAnalytics(eventId);
        log.debug("Returning event analytics for {}: {}", eventId, eventStats);

        return eventStats;
    }

    @GetMapping(Api.PAYMENT_TRENDS_PATH)
    public List<PaymentTrend> getPaymentTrends(
            @RequestParam(name = Api.PARAM_DAYS,
                    defaultValue = Api.DEFAULT_DAYS) int days)
    {
        final List<PaymentTrend> paymentTrendList = analyticsQueryService.getPaymentTrends(days);
        log.debug("Returning payment trends for the past {} days: {}", days, paymentTrendList);

        return paymentTrendList;
    }

    @GetMapping(Api.EVENT_LIFECYCLE_PATH)
    public List<EventLifecycleTrend> getEventLifecycleTrends(
            @RequestParam(name = Api.PARAM_DAYS,
                    defaultValue = Api.DEFAULT_DAYS) int days)
    {
        final List<EventLifecycleTrend> lifecycleTrendList = analyticsQueryService.getEventLifecycleTrends(days);
        log.debug("Returning event lifecycle trends for the past {} days: {}", days, lifecycleTrendList);

        return lifecycleTrendList;
    }
}
