package com.booking.platform.graphql_gateway.dto.lovelist;

import com.booking.platform.common.grpc.booking.LoveListItem;

/**
 * GraphQL DTO for one lovelist entry. Maps from the gRPC {@code LoveListItem}. The live
 * {@code event} field is resolved separately by the lovelist resolver (hydration).
 */
public record LovelistItem(
        String eventId,
        String createdAt
) {
    public static LovelistItem fromGrpc(LoveListItem item) {
        return new LovelistItem(
                item.getEventId(),
                item.getCreatedAt().isBlank() ? null : item.getCreatedAt()
        );
    }
}
