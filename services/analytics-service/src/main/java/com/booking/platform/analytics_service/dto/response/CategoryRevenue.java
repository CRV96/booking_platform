package com.booking.platform.analytics_service.dto.response;

import lombok.Builder;

import java.io.Serializable;

@Builder
public record CategoryRevenue
        (
                String category,
                double totalRevenue,
                int totalBookings
        )
        implements Serializable {}
