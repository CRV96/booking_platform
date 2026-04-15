package com.booking.platform.analytics_service.controller;

import com.booking.platform.analytics_service.constants.AnalyticsConstants.Api;
import com.booking.platform.analytics_service.dto.response.*;
import com.booking.platform.analytics_service.service.AnalyticsQueryService;
import com.booking.platform.common.logging.ApplicationLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@RestController
@RequestMapping(Api.ANALYTICS_BASE_PATH)
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsQueryService analyticsQueryService;

    @GetMapping(Api.TOP_REVENUE_PATH)
    public List<TopRevenueEvent> getTopEventsByRevenue(
            @RequestParam(name = Api.PARAM_LIMIT, defaultValue = Api.DEFAULT_LIMIT) int limit) {
        List<TopRevenueEvent> result = analyticsQueryService.getTopEventsByRevenue(limit);
        ApplicationLogger.logMessage(log, Level.DEBUG, "Returning top {} events by revenue: {}", limit, result);
        return result;
    }

    @GetMapping(Api.BOOKING_TRENDS_PATH)
    public List<BookingTrend> getBookingTrends(
            @RequestParam(name = Api.PARAM_DAYS, defaultValue = Api.DEFAULT_DAYS) int days) {
        List<BookingTrend> result = analyticsQueryService.getBookingTrends(days);
        ApplicationLogger.logMessage(log, Level.DEBUG, "Returning booking trends for the past {} days: {}", days, result);
        return result;
    }

    @GetMapping(Api.REVENUE_BY_CATEGORY_PATH)
    public List<CategoryRevenue> getRevenueByCategory() {
        List<CategoryRevenue> result = analyticsQueryService.getRevenueByCategory();
        ApplicationLogger.logMessage(log, Level.DEBUG, "Returning revenue by category: {}", result);
        return result;
    }

    @GetMapping(Api.CANCELLATION_RATE_PATH)
    public CancellationRate getCancellationRate() {
        CancellationRate result = analyticsQueryService.getCancellationRate();
        ApplicationLogger.logMessage(log, Level.DEBUG, "Returning cancellation rate: {}", result);
        return result;
    }

    @GetMapping(Api.AVG_BOOKING_VALUE_PATH)
    public AverageBookingValue getAverageBookingValue() {
        AverageBookingValue result = analyticsQueryService.getAverageBookingValue();
        ApplicationLogger.logMessage(log, Level.DEBUG, "Returning average booking value: {}", result);
        return result;
    }

    @GetMapping(Api.ALL_EVENTS_PATH)
    public List<EventStatsDetail> getAllEventStats() {
        List<EventStatsDetail> result = analyticsQueryService.getAllEventStats();
        ApplicationLogger.logMessage(log, Level.DEBUG, "Returning all event stats ({} events)", result.size());
        return result;
    }

    @GetMapping(Api.EVENT_ANALYTICS_PATH)
    public EventStatsDetail getEventAnalytics(@PathVariable(Api.PARAM_EVENT_ID) String eventId) {
        EventStatsDetail result = analyticsQueryService.getEventAnalytics(eventId);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No analytics found for eventId: " + eventId);
        }
        ApplicationLogger.logMessage(log, Level.DEBUG, "Returning event analytics for {}: {}", eventId, result);
        return result;
    }

    @GetMapping(Api.PAYMENT_TRENDS_PATH)
    public List<PaymentTrend> getPaymentTrends(
            @RequestParam(name = Api.PARAM_DAYS, defaultValue = Api.DEFAULT_DAYS) int days) {
        List<PaymentTrend> result = analyticsQueryService.getPaymentTrends(days);
        ApplicationLogger.logMessage(log, Level.DEBUG, "Returning payment trends for the past {} days: {}", days, result);
        return result;
    }

    @GetMapping(Api.EVENT_LIFECYCLE_PATH)
    public List<EventLifecycleTrend> getEventLifecycleTrends(
            @RequestParam(name = Api.PARAM_DAYS, defaultValue = Api.DEFAULT_DAYS) int days) {
        List<EventLifecycleTrend> result = analyticsQueryService.getEventLifecycleTrends(days);
        ApplicationLogger.logMessage(log, Level.DEBUG, "Returning event lifecycle trends for the past {} days: {}", days, result);
        return result;
    }
}
