package com.booking.platform.analytics_service.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record EventDto(String topic, String key,
                       String eventId, String title, String category,
                       List<String> changedFields, String reason) {
}
