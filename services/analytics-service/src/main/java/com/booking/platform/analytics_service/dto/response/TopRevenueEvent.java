package com.booking.platform.analytics_service.dto.response;

import java.io.Serializable;

public record TopRevenueEvent(String eventId, String eventTitle, String category,
                              double totalRevenue, int confirmedBookings,
                              String currency) implements Serializable {
}
