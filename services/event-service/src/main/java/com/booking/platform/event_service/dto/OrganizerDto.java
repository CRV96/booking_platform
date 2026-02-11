package com.booking.platform.event_service.dto;

import lombok.Builder;

@Builder
public record OrganizerDto(String userId, String name, String email) {
}
