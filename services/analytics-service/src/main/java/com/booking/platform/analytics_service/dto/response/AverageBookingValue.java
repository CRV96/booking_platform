package com.booking.platform.analytics_service.dto.response;

import lombok.Builder;

import java.io.Serializable;

@Builder
public record AverageBookingValue
        (
                double totalRevenue,
                long totalConfirmedBookings,
                double averageValue
        )
        implements Serializable {}
