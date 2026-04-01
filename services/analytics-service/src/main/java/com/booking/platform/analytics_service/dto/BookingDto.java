package com.booking.platform.analytics_service.dto;

import lombok.Builder;

@Builder
public record BookingDto
        (
                String topic,
                String key,
                String bookingId,
                String eventId,
                double totalPrice,
                String currency,
                String eventTitle,
                String seatCategory,
                String reason
        )
{}
