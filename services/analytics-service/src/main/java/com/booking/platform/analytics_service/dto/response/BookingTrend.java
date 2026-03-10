package com.booking.platform.analytics_service.dto.response;

import java.io.Serializable;

public record BookingTrend(String date, int bookingsCreated, int bookingsConfirmed,
                           int bookingsCancelled, double totalRevenue) implements Serializable {
}
