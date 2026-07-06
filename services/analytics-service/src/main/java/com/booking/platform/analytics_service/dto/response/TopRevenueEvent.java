package com.booking.platform.analytics_service.dto.response;

import lombok.Builder;

import java.io.Serializable;

@Builder
public record TopRevenueEvent
        (
                String eventId,
                String eventTitle,
                String category,
                double totalRevenue,
                int confirmedBookings,
                String currency
        )
        implements Serializable {}
