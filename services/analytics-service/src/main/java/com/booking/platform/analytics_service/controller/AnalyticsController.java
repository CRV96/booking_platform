package com.booking.platform.analytics_service.controller;

import com.booking.platform.analytics_service.constants.BkgAnalyticsConstants;
import com.booking.platform.analytics_service.dto.response.*;
import com.booking.platform.analytics_service.service.AnalyticsQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(BkgAnalyticsConstants.BkgControllerConstants.ANALYTICS_BASE_PATH)
@RequiredArgsConstructor
@Log4j2
public class AnalyticsController {

    private final AnalyticsQueryService analyticsQueryService;

    @GetMapping(BkgAnalyticsConstants.BkgControllerConstants.TOP_REVENUE_PATH)
    public List<TopRevenueEvent> getTopEventsByRevenue(
            @RequestParam(name = BkgAnalyticsConstants.BkgControllerConstants.PARAM_LIMIT,
                    defaultValue = BkgAnalyticsConstants.BkgControllerConstants.DEFAULT_LIMIT) int limit)
    {
            final List<TopRevenueEvent> topRevenueEventList = analyticsQueryService.getTopEventsByRevenue(limit);
            log.debug("Returning top {} events by revenue: {}", limit, topRevenueEventList);

            return topRevenueEventList;
    }

    @GetMapping(BkgAnalyticsConstants.BkgControllerConstants.BOOKING_TRENDS_PATH)
    public List<BookingTrend> getBookingTrends(
            @RequestParam(name = BkgAnalyticsConstants.BkgControllerConstants.PARAM_DAYS,
                    defaultValue = BkgAnalyticsConstants.BkgControllerConstants.DEFAULT_DAYS) int days)
    {
        final List<BookingTrend> bookingTrendList = analyticsQueryService.getBookingTrends(days);
        log.debug("Returning booking trends for the past {} days: {}", days, bookingTrendList);

        return bookingTrendList;
    }

    @GetMapping(BkgAnalyticsConstants.BkgControllerConstants.REVENUE_BY_CATEGORY_PATH)
    public List<CategoryRevenue> getRevenueByCategory() {
        final List<CategoryRevenue> categoryRevenueList = analyticsQueryService.getRevenueByCategory();
        log.debug("Returning revenue by category: {}", categoryRevenueList);

        return categoryRevenueList;
    }

    @GetMapping(BkgAnalyticsConstants.BkgControllerConstants.CANCELLATION_RATE_PATH)
    public CancellationRate getCancellationRate() {
        final CancellationRate cancellationRate = analyticsQueryService.getCancellationRate();
        log.debug("Returning cancellation rate: {}", cancellationRate);

        return cancellationRate;
    }

    @GetMapping(BkgAnalyticsConstants.BkgControllerConstants.AVG_BOOKING_VALUE_PATH)
    public AverageBookingValue getAverageBookingValue() {
        final AverageBookingValue averageBookingValue = analyticsQueryService.getAverageBookingValue();
        log.debug("Returning average booking value: {}", averageBookingValue);

        return averageBookingValue;
    }
}
