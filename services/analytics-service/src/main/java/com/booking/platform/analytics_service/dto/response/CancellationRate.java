package com.booking.platform.analytics_service.dto.response;

import java.io.Serializable;

public record CancellationRate(long totalBookingsCreated, long totalBookingsCancelled,
                               double cancellationRate) implements Serializable {
}
