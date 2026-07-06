package com.booking.platform.analytics_service.dto.response;

import lombok.Builder;

import java.io.Serializable;

@Builder
public record EventLifecycleTrend
        (
                String date,
                int eventsCreated,
                int eventsPublished,
                int eventsCancelled
        )
        implements Serializable {}
