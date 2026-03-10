package com.booking.platform.analytics_service.dto.response;

import lombok.Builder;

import java.io.Serializable;

@Builder
public record EventStatsDetail
        (
                String eventId,
                String eventTitle,
                String category,
                int totalBookings,
                int confirmedBookings,
                int cancelledBookings,
                double totalRevenue,
                double totalRefunds,
                String currency
        )
        implements Serializable {}
