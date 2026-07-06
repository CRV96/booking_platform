package com.booking.platform.graphql_gateway.dto.event;

/**
 * GraphQL DTO representing organizer information embedded in an event.
 */
public record OrganizerInfo(
        String userId,
        String name,
        String email
) {
    public static OrganizerInfo fromGrpc(com.booking.platform.common.grpc.event.OrganizerInfo organizerInfo) {
        return new OrganizerInfo(
                organizerInfo.getUserId(),
                organizerInfo.getName(),
                organizerInfo.getEmail()
        );
    }
}
