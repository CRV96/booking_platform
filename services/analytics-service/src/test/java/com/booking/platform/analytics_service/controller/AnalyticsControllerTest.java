package com.booking.platform.analytics_service.controller;

import com.booking.platform.analytics_service.constants.AnalyticsConstants.Api;
import com.booking.platform.analytics_service.dto.response.*;
import com.booking.platform.analytics_service.service.AnalyticsQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    @Mock private AnalyticsQueryService analyticsQueryService;

    @InjectMocks private AnalyticsController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static final String BASE = Api.ANALYTICS_BASE_PATH;

    // ── GET /top-revenue ──────────────────────────────────────────────────────

    @Test
    void getTopEventsByRevenue_defaultLimit_returns200() throws Exception {
        when(analyticsQueryService.getTopEventsByRevenue(10)).thenReturn(List.of());

        mockMvc.perform(get(BASE + Api.TOP_REVENUE_PATH).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        verify(analyticsQueryService).getTopEventsByRevenue(10);
    }

    @Test
    void getTopEventsByRevenue_explicitLimit_passedToService() throws Exception {
        when(analyticsQueryService.getTopEventsByRevenue(5)).thenReturn(List.of());

        mockMvc.perform(get(BASE + Api.TOP_REVENUE_PATH).param("limit", "5"))
                .andExpect(status().isOk());

        verify(analyticsQueryService).getTopEventsByRevenue(5);
    }

    // ── GET /booking-trends ───────────────────────────────────────────────────

    @Test
    void getBookingTrends_defaultDays_returns200() throws Exception {
        when(analyticsQueryService.getBookingTrends(30)).thenReturn(List.of());

        mockMvc.perform(get(BASE + Api.BOOKING_TRENDS_PATH))
                .andExpect(status().isOk());

        verify(analyticsQueryService).getBookingTrends(30);
    }

    @Test
    void getBookingTrends_explicitDays_passedToService() throws Exception {
        when(analyticsQueryService.getBookingTrends(7)).thenReturn(List.of());

        mockMvc.perform(get(BASE + Api.BOOKING_TRENDS_PATH).param("days", "7"))
                .andExpect(status().isOk());

        verify(analyticsQueryService).getBookingTrends(7);
    }

    // ── GET /revenue-by-category ──────────────────────────────────────────────

    @Test
    void getRevenueByCategory_returns200() throws Exception {
        when(analyticsQueryService.getRevenueByCategory()).thenReturn(List.of(
                new CategoryRevenue("CONCERT", 500.0, 10)));

        mockMvc.perform(get(BASE + Api.REVENUE_BY_CATEGORY_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("CONCERT"));
    }

    // ── GET /cancellation-rate ────────────────────────────────────────────────

    @Test
    void getCancellationRate_returns200() throws Exception {
        when(analyticsQueryService.getCancellationRate())
                .thenReturn(new CancellationRate(100, 20, 0.2));

        mockMvc.perform(get(BASE + Api.CANCELLATION_RATE_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellationRate").value(0.2));
    }

    // ── GET /avg-booking-value ────────────────────────────────────────────────

    @Test
    void getAverageBookingValue_returns200() throws Exception {
        when(analyticsQueryService.getAverageBookingValue())
                .thenReturn(new AverageBookingValue(500.0, 10, 50.0));

        mockMvc.perform(get(BASE + Api.AVG_BOOKING_VALUE_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageValue").value(50.0));
    }

    // ── GET /events ───────────────────────────────────────────────────────────

    @Test
    void getAllEventStats_returns200WithList() throws Exception {
        when(analyticsQueryService.getAllEventStats()).thenReturn(List.of(
                new EventStatsDetail("ev-1", "Fest", "CONCERT", 10, 8, 2, 800.0, 0.0, "USD")));

        mockMvc.perform(get(BASE + Api.ALL_EVENTS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value("ev-1"));
    }

    // ── GET /event/{eventId} ──────────────────────────────────────────────────

    @Test
    void getEventAnalytics_found_returns200() throws Exception {
        when(analyticsQueryService.getEventAnalytics("ev-1"))
                .thenReturn(new EventStatsDetail("ev-1", "Fest", "CONCERT", 5, 4, 1, 400.0, 0.0, "USD"));

        mockMvc.perform(get(BASE + "/event/ev-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("ev-1"));
    }

    @Test
    void getEventAnalytics_notFound_returns404() throws Exception {
        when(analyticsQueryService.getEventAnalytics("ev-missing")).thenReturn(null);

        mockMvc.perform(get(BASE + "/event/ev-missing"))
                .andExpect(status().isNotFound());
    }

    // ── GET /payment-trends ───────────────────────────────────────────────────

    @Test
    void getPaymentTrends_defaultDays_returns200() throws Exception {
        when(analyticsQueryService.getPaymentTrends(30)).thenReturn(List.of());

        mockMvc.perform(get(BASE + Api.PAYMENT_TRENDS_PATH))
                .andExpect(status().isOk());

        verify(analyticsQueryService).getPaymentTrends(30);
    }

    @Test
    void getPaymentTrends_explicitDays_passedToService() throws Exception {
        when(analyticsQueryService.getPaymentTrends(14)).thenReturn(List.of());

        mockMvc.perform(get(BASE + Api.PAYMENT_TRENDS_PATH).param("days", "14"))
                .andExpect(status().isOk());

        verify(analyticsQueryService).getPaymentTrends(14);
    }

    // ── GET /event-lifecycle ──────────────────────────────────────────────────

    @Test
    void getEventLifecycleTrends_defaultDays_returns200() throws Exception {
        when(analyticsQueryService.getEventLifecycleTrends(30)).thenReturn(List.of());

        mockMvc.perform(get(BASE + Api.EVENT_LIFECYCLE_PATH))
                .andExpect(status().isOk());

        verify(analyticsQueryService).getEventLifecycleTrends(30);
    }
}
