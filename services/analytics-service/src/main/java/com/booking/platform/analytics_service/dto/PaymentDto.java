package com.booking.platform.analytics_service.dto;

import lombok.Builder;

@Builder
public record PaymentDto
        (
                String topic,
                String key,
                String paymentId,
                String bookingId,
                double amount,
                String currency,
                String reason,
                String refundId
        )
{}
