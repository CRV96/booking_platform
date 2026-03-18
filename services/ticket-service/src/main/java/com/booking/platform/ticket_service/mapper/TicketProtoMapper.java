package com.booking.platform.ticket_service.mapper;

import com.booking.platform.common.grpc.ticket.TicketInfo;
import com.booking.platform.ticket_service.document.TicketDocument;
import org.mapstruct.Mapper;

import java.time.Instant;
import java.util.List;

/**
 * MapStruct mapper for converting between {@link TicketDocument} (MongoDB) and
 * {@link TicketInfo} (Protobuf).
 *
 * <p>Protobuf classes use builders and are immutable — MapStruct can't set fields directly,
 * so we use default methods with manual builder calls.
 */
@Mapper(componentModel = "spring")
public interface TicketProtoMapper {

    default TicketInfo toProto(TicketDocument doc) {
        if (doc == null) return null;

        return TicketInfo.newBuilder()
                .setId(nullToEmpty(doc.getId()))
                .setBookingId(nullToEmpty(doc.getBookingId()))
                .setEventId(nullToEmpty(doc.getEventId()))
                .setUserId(nullToEmpty(doc.getUserId()))
                .setTicketNumber(nullToEmpty(doc.getTicketNumber()))
                .setQrCodeData(nullToEmpty(doc.getQrCodeData()))
                .setSeatCategory(nullToEmpty(doc.getSeatCategory()))
                .setSeatNumber(nullToEmpty(doc.getSeatNumber()))
                .setStatus(doc.getStatus() != null ? doc.getStatus().name() : "")
                .setEventTitle(nullToEmpty(doc.getEventTitle()))
                .setCreatedAt(instantToString(doc.getCreatedAt()))
                .build();
    }

    default List<TicketInfo> toProtoList(List<TicketDocument> docs) {
        if (docs == null) return List.of();
        return docs.stream().map(this::toProto).toList();
    }

    default String instantToString(Instant instant) {
        return instant != null ? instant.toString() : "";
    }

    default String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
