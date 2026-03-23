package com.booking.platform.event_service.dto;

import lombok.Builder;

/** Data Transfer Object for event organizer information (denormalized from user-service/Keycloak). */
@Builder
public record OrganizerDto
        (
                String userId,
                String name,
                String email
        )
{}
