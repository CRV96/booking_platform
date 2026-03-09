package com.booking.platform.analytics_service.controller;

import com.booking.platform.analytics_service.dto.response.*;
import com.booking.platform.analytics_service.service.AnalyticsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsQueryService analyticsQueryService;

    @GetMapping("/top-revenue")
    public List<TopRevenueEvent> getTopEventsByRevenue(
            @RequestParam(defaultValue = "10") int limit) {
        return analyticsQueryService.getTopEventsByRevenue(limit);
    }

    @GetMapping("/booking-trends")
    public List<BookingTrend> getBookingTrends(
            @RequestParam(defaultValue = "30") int days) {
        return analyticsQueryService.getBookingTrends(days);
    }

    @GetMapping("/revenue-by-category")
    public List<CategoryRevenue> getRevenueByCategory() {
        return analyticsQueryService.getRevenueByCategory();
    }

    @GetMapping("/cancellation-rate")
    public CancellationRate getCancellationRate() {
        return analyticsQueryService.getCancellationRate();
    }

    @GetMapping("/avg-booking-value")
    public AverageBookingValue getAverageBookingValue() {
        return analyticsQueryService.getAverageBookingValue();
    }
}
