package com.booking.platform.booking_service.mapper;

import com.booking.platform.booking_service.entity.BookingEntity;
import com.booking.platform.common.grpc.booking.BookingInfo;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Maps {@link BookingEntity} to gRPC {@link BookingInfo} protobuf messages.
 *
 * <p>Prices are serialised as decimal strings (e.g. "49.99") to avoid
 * floating-point precision loss over the wire. Timestamps are formatted
 * as ISO 8601 strings.</p>
 */
@Component
public class BookingMapper {

    /**
     * Converts a single entity to its protobuf representation.
     */
    public BookingInfo toProto(BookingEntity entity) {
        BookingInfo.Builder builder = BookingInfo.newBuilder()
                .setId(entity.getId().toString())
                .setUserId(entity.getUserId())
                .setEventId(entity.getEventId())
                .setEventTitle(entity.getEventTitle())
                .setStatus(entity.getStatus().name())
                .setSeatCategory(entity.getSeatCategory())
                .setQuantity(entity.getQuantity())
                .setUnitPrice(entity.getUnitPrice().toPlainString())
                .setTotalPrice(entity.getTotalPrice().toPlainString())
                .setCurrency(entity.getCurrency())
                .setIdempotencyKey(entity.getIdempotencyKey())
                .setHoldExpiresAt(formatInstant(entity.getHoldExpiresAt()))
                .setCreatedAt(formatInstant(entity.getCreatedAt()))
                .setUpdatedAt(formatInstant(entity.getUpdatedAt()));

        if (entity.getCancellationReason() != null) {
            builder.setCancellationReason(entity.getCancellationReason());
        }

        return builder.build();
    }

    /**
     * Converts a list of entities to protobuf messages.
     */
    public List<BookingInfo> toProtoList(List<BookingEntity> entities) {
        return entities.stream()
                .map(this::toProto)
                .toList();
    }

    private String formatInstant(Instant instant) {
        return instant != null ? instant.toString() : "";
    }
}
