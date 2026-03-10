package com.booking.platform.analytics_service.dto.response;

import lombok.Builder;

import java.io.Serializable;

@Builder
public record CancellationRate
        (
                long totalBookingsCreated,
                long totalBookingsCancelled,
                double cancellationRate
        )
        implements Serializable {}
