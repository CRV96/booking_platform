package com.booking.platform.booking_service.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record AddCartItemDto(
        String userId,
        String eventId,
        String eventTitle,
        String seatCategory,
        int quantity,
        BigDecimal unitPrice,
        String currency
) {}
