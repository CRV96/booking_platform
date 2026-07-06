package com.booking.platform.analytics_service.dto.response;

import lombok.Builder;

import java.io.Serializable;

@Builder
public record PaymentTrend
        (
                String date,
                int paymentsCompleted,
                int paymentsFailed,
                int refundsCompleted,
                double totalRefunds
        )
        implements Serializable {}
