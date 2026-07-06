package com.booking.platform.graphql_gateway.dto.booking;

import com.booking.platform.common.grpc.booking.BookingInfo;

/**
 * GraphQL DTO representing a booking.
 * Maps from the gRPC BookingInfo protobuf message.
 *
 * <p>Prices are kept as strings (not doubles) to preserve decimal precision.
 * Timestamps are ISO 8601 strings from the booking-service.</p>
 */
public record Booking(
        String id,
        String userId,
        String eventId,
        String eventTitle,
        String status,
        String seatCategory,
        int quantity,
        String unitPrice,
        String totalPrice,
        String currency,
        String idempotencyKey,
        String holdExpiresAt,
        String cancellationReason,
        String createdAt,
        String updatedAt
) {
    /**
     * Maps a gRPC BookingInfo to a GraphQL Booking DTO.
     */
    public static Booking fromGrpc(BookingInfo info) {
        return new Booking(
                info.getId(),
                info.getUserId(),
                info.getEventId(),
                info.getEventTitle(),
                info.getStatus(),
                info.getSeatCategory(),
                info.getQuantity(),
                info.getUnitPrice(),
                info.getTotalPrice(),
                info.getCurrency(),
                info.getIdempotencyKey(),
                info.getHoldExpiresAt().isBlank() ? null : info.getHoldExpiresAt(),
                info.hasCancellationReason() ? info.getCancellationReason() : null,
                info.getCreatedAt().isBlank() ? null : info.getCreatedAt(),
                info.getUpdatedAt().isBlank() ? null : info.getUpdatedAt()
        );
    }
}
