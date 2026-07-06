package com.booking.platform.analytics_service.dto.response;

import lombok.Builder;

import java.io.Serializable;

@Builder
public record BookingTrend
        (
            String date,
            int bookingsCreated,
            int bookingsConfirmed,
            int bookingsCancelled,
            double totalRevenue
        )
        implements Serializable {}
