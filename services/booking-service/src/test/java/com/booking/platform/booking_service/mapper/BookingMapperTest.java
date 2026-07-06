package com.booking.platform.booking_service.mapper;

import com.booking.platform.booking_service.entity.BookingEntity;
import com.booking.platform.booking_service.entity.enums.BookingStatus;
import com.booking.platform.common.grpc.booking.BookingInfo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookingMapperTest {

    private final BookingMapper mapper = new BookingMapper();

    private static final UUID ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2025-06-15T12:00:00Z");

    private BookingEntity fullEntity() {
        return BookingEntity.builder()
                .id(ID)
                .userId("u-1")
                .eventId("ev-1")
                .eventTitle("Rock Fest")
                .status(BookingStatus.CONFIRMED)
                .seatCategory("VIP")
                .quantity(2)
                .unitPrice(new BigDecimal("49.99"))
                .totalPrice(new BigDecimal("99.98"))
                .currency("USD")
                .idempotencyKey("idem-key")
                .holdExpiresAt(NOW)
                .cancellationReason(null)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    // ── toProto ───────────────────────────────────────────────────────────────

    @Test
    void toProto_mapsIdAndUserFields() {
        BookingInfo proto = mapper.toProto(fullEntity());

        assertThat(proto.getId()).isEqualTo(ID.toString());
        assertThat(proto.getUserId()).isEqualTo("u-1");
        assertThat(proto.getEventId()).isEqualTo("ev-1");
        assertThat(proto.getEventTitle()).isEqualTo("Rock Fest");
    }

    @Test
    void toProto_mapsStatusAndCategory() {
        BookingInfo proto = mapper.toProto(fullEntity());

        assertThat(proto.getStatus()).isEqualTo("CONFIRMED");
        assertThat(proto.getSeatCategory()).isEqualTo("VIP");
        assertThat(proto.getQuantity()).isEqualTo(2);
    }

    @Test
    void toProto_pricesSerializedAsDecimalStrings() {
        BookingInfo proto = mapper.toProto(fullEntity());

        assertThat(proto.getUnitPrice()).isEqualTo("49.99");
        assertThat(proto.getTotalPrice()).isEqualTo("99.98");
        assertThat(proto.getCurrency()).isEqualTo("USD");
    }

    @Test
    void toProto_mapsTimestampsAsIso8601() {
        BookingInfo proto = mapper.toProto(fullEntity());

        assertThat(proto.getHoldExpiresAt()).isEqualTo("2025-06-15T12:00:00Z");
        assertThat(proto.getCreatedAt()).isEqualTo("2025-06-15T12:00:00Z");
        assertThat(proto.getUpdatedAt()).isEqualTo("2025-06-15T12:00:00Z");
    }

    @Test
    void toProto_nullTimestamps_returnEmptyString() {
        BookingEntity entity = fullEntity();
        entity.setCreatedAt(null);
        entity.setUpdatedAt(null);
        entity.setHoldExpiresAt(null);

        BookingInfo proto = mapper.toProto(entity);

        assertThat(proto.getCreatedAt()).isEmpty();
        assertThat(proto.getUpdatedAt()).isEmpty();
        assertThat(proto.getHoldExpiresAt()).isEmpty();
    }

    @Test
    void toProto_withCancellationReason_includesField() {
        BookingEntity entity = fullEntity();
        entity.setCancellationReason("changed mind");

        BookingInfo proto = mapper.toProto(entity);

        assertThat(proto.getCancellationReason()).isEqualTo("changed mind");
    }

    @Test
    void toProto_nullCancellationReason_fieldAbsent() {
        BookingInfo proto = mapper.toProto(fullEntity()); // cancellationReason = null

        assertThat(proto.getCancellationReason()).isEmpty();
    }

    @Test
    void toProto_mapsIdempotencyKey() {
        BookingInfo proto = mapper.toProto(fullEntity());

        assertThat(proto.getIdempotencyKey()).isEqualTo("idem-key");
    }

    // ── toProtoList ───────────────────────────────────────────────────────────

    @Test
    void toProtoList_emptyList_returnsEmptyList() {
        assertThat(mapper.toProtoList(List.of())).isEmpty();
    }

    @Test
    void toProtoList_mapsEachEntity() {
        List<BookingInfo> result = mapper.toProtoList(List.of(fullEntity(), fullEntity()));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(ID.toString());
    }
}
