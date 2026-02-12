package com.booking.platform.graphql_gateway.dto.event;

import com.booking.platform.common.grpc.event.EventInfo;
import com.booking.platform.common.grpc.event.SeatCategoryInfo;

import java.util.List;

/**
 * GraphQL DTO representing an event.
 * Maps from the gRPC EventInfo protobuf message.
 */
public record Event(
        String id,
        String title,
        String description,
        String category,
        String status,
        String dateTime,
        VenueInfo venue,
        OrganizerInfo organizer,
        List<SeatCategory> seatCategories,
        String createdAt,
        String updatedAt
) {
    /**
     * Maps a gRPC EventInfo to a GraphQL Event DTO.
     */
    public static Event fromGrpc(EventInfo eventInfo) {
        return new Event(
                eventInfo.getId(),
                eventInfo.getTitle(),
                eventInfo.getDescription().isBlank() ? null : eventInfo.getDescription(),
                eventInfo.getCategory(),
                eventInfo.getStatus(),
                eventInfo.getDateTime(),
                VenueInfo.fromGrpc(eventInfo.getVenue()),
                OrganizerInfo.fromGrpc(eventInfo.getOrganizer()),
                eventInfo.getSeatCategoriesList().stream()
                        .map(SeatCategory::fromGrpc)
                        .toList(),
                eventInfo.getCreatedAt().isBlank() ? null : eventInfo.getCreatedAt(),
                eventInfo.getUpdatedAt().isBlank() ? null : eventInfo.getUpdatedAt()
        );
    }
}
