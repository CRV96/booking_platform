package com.booking.platform.analytics_service.dto.response;

import java.io.Serializable;

public record AverageBookingValue(double totalRevenue, long totalConfirmedBookings,
                                  double averageValue) implements Serializable {
}
