package com.booking.platform.ticket_service.mapper;

import com.booking.platform.common.grpc.ticket.TicketInfo;
import com.booking.platform.ticket_service.document.TicketDocument;
import com.booking.platform.ticket_service.document.enums.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TicketProtoMapperTest {

    // All mapper methods are default interface methods — instantiated directly.
    private final TicketProtoMapper mapper = new TicketProtoMapper() {};

    private TicketDocument fullDocument() {
        return TicketDocument.builder()
                .id("mongo-id-1")
                .bookingId("booking-1")
                .eventId("event-1")
                .userId("user-1")
                .ticketNumber("TKT-20240101-ABCDEF")
                .qrCodeData("qr-uuid-123")
                .seatCategory("VIP")
                .seatNumber("A12")
                .status(TicketStatus.VALID)
                .eventTitle("Concert Night")
                .createdAt(Instant.parse("2024-01-01T10:00:00Z"))
                .build();
    }

    // ── toProto ───────────────────────────────────────────────────────────────

    @Test
    void toProto_null_returnsNull() {
        assertThat(mapper.toProto(null)).isNull();
    }

    @Test
    void toProto_fullDocument_mapsAllFields() {
        TicketInfo info = mapper.toProto(fullDocument());

        assertThat(info.getId()).isEqualTo("mongo-id-1");
        assertThat(info.getBookingId()).isEqualTo("booking-1");
        assertThat(info.getEventId()).isEqualTo("event-1");
        assertThat(info.getUserId()).isEqualTo("user-1");
        assertThat(info.getTicketNumber()).isEqualTo("TKT-20240101-ABCDEF");
        assertThat(info.getQrCodeData()).isEqualTo("qr-uuid-123");
        assertThat(info.getSeatCategory()).isEqualTo("VIP");
        assertThat(info.getSeatNumber()).isEqualTo("A12");
        assertThat(info.getStatus()).isEqualTo("VALID");
        assertThat(info.getEventTitle()).isEqualTo("Concert Night");
        assertThat(info.getCreatedAt()).isEqualTo("2024-01-01T10:00:00Z");
    }

    @Test
    void toProto_nullStringFields_mappedToEmpty() {
        TicketDocument doc = TicketDocument.builder()
                .id(null)
                .bookingId(null)
                .eventId(null)
                .userId(null)
                .ticketNumber(null)
                .qrCodeData(null)
                .seatCategory(null)
                .seatNumber(null)
                .status(TicketStatus.VALID)
                .eventTitle(null)
                .createdAt(null)
                .build();

        TicketInfo info = mapper.toProto(doc);

        assertThat(info.getId()).isEmpty();
        assertThat(info.getBookingId()).isEmpty();
        assertThat(info.getEventId()).isEmpty();
        assertThat(info.getUserId()).isEmpty();
        assertThat(info.getTicketNumber()).isEmpty();
        assertThat(info.getQrCodeData()).isEmpty();
        assertThat(info.getSeatCategory()).isEmpty();
        assertThat(info.getSeatNumber()).isEmpty();
        assertThat(info.getEventTitle()).isEmpty();
        assertThat(info.getCreatedAt()).isEmpty();
    }

    @Test
    void toProto_nullStatus_mappedToEmpty() {
        TicketDocument doc = TicketDocument.builder()
                .id("id-1")
                .status(null)
                .build();

        assertThat(mapper.toProto(doc).getStatus()).isEmpty();
    }

    @Test
    void toProto_cancelledStatus_mappedCorrectly() {
        TicketDocument doc = TicketDocument.builder().id("x").status(TicketStatus.CANCELLED).build();
        assertThat(mapper.toProto(doc).getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void toProto_usedStatus_mappedCorrectly() {
        TicketDocument doc = TicketDocument.builder().id("x").status(TicketStatus.USED).build();
        assertThat(mapper.toProto(doc).getStatus()).isEqualTo("USED");
    }

    // ── toProtoList ───────────────────────────────────────────────────────────

    @Test
    void toProtoList_null_returnsEmpty() {
        assertThat(mapper.toProtoList(null)).isEmpty();
    }

    @Test
    void toProtoList_empty_returnsEmpty() {
        assertThat(mapper.toProtoList(List.of())).isEmpty();
    }

    @Test
    void toProtoList_multipleDocuments_mapsAll() {
        TicketDocument d1 = TicketDocument.builder().id("id-1").ticketNumber("TKT-A").status(TicketStatus.VALID).build();
        TicketDocument d2 = TicketDocument.builder().id("id-2").ticketNumber("TKT-B").status(TicketStatus.USED).build();

        List<TicketInfo> result = mapper.toProtoList(List.of(d1, d2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("id-1");
        assertThat(result.get(1).getId()).isEqualTo("id-2");
    }

    // ── instantToString ───────────────────────────────────────────────────────

    @Test
    void instantToString_null_returnsEmpty() {
        assertThat(mapper.instantToString(null)).isEmpty();
    }

    @Test
    void instantToString_instant_returnsIsoString() {
        Instant instant = Instant.parse("2024-06-15T12:30:00Z");
        assertThat(mapper.instantToString(instant)).isEqualTo("2024-06-15T12:30:00Z");
    }

    // ── nullToEmpty ───────────────────────────────────────────────────────────

    @Test
    void nullToEmpty_null_returnsEmpty() {
        assertThat(mapper.nullToEmpty(null)).isEmpty();
    }

    @Test
    void nullToEmpty_value_returnsValue() {
        assertThat(mapper.nullToEmpty("hello")).isEqualTo("hello");
    }
}
