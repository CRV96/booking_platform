package com.booking.platform.analytics_service.dto.response;

import java.io.Serializable;

public record CategoryRevenue(String category, double totalRevenue,
                              int totalBookings) implements Serializable {
}
