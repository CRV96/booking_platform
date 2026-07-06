package com.booking.platform.event_service.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Embedded document for event organizer identity (denormalized from user-service/Keycloak). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizerInfo {

    private String userId;
    private String name;
    private String email;
}
