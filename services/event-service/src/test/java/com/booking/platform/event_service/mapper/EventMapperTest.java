package com.booking.platform.event_service.mapper;

import com.booking.platform.common.grpc.event.EventInfo;
import com.booking.platform.common.grpc.event.OrganizerInfo;
import com.booking.platform.common.grpc.event.SeatCategoryInfo;
import com.booking.platform.common.grpc.event.VenueInfo;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.document.SeatCategory;
import com.booking.platform.event_service.document.enums.EventCategory;
import com.booking.platform.event_service.document.enums.EventStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventMapperTest {

    // Anonymous inner class — no Spring context needed
    private final EventMapper mapper = new EventMapper() {};

    private static final Instant NOW = Instant.parse("2025-06-15T12:00:00Z");

    private com.booking.platform.event_service.document.VenueInfo venue() {
        return com.booking.platform.event_service.document.VenueInfo.builder()
                .name("Arena").address("Main St 1").city("Berlin").country("DE")
                .latitude(52.52).longitude(13.40).capacity(5000)
                .build();
    }

    private com.booking.platform.event_service.document.OrganizerInfo organizer() {
        return com.booking.platform.event_service.document.OrganizerInfo.builder()
                .userId("u-1").name("Alice").email("alice@example.com")
                .build();
    }

    private SeatCategory seatCategory() {
        return SeatCategory.builder()
                .name("VIP").price(150.0).currency("USD")
                .totalSeats(50).availableSeats(30)
                .build();
    }

    private EventDocument fullEvent() {
        return EventDocument.builder()
                .id("ev-1")
                .title("Rock Fest")
                .description("A great festival")
                .category(EventCategory.CONCERT)
                .status(EventStatus.PUBLISHED)
                .timezone("UTC")
                .dateTime(NOW)
                .endDateTime(NOW.plusSeconds(7200))
                .createdAt(NOW)
                .updatedAt(NOW)
                .venue(venue())
                .organizer(organizer())
                .seatCategories(List.of(seatCategory()))
                .images(List.of("img1.jpg", "img2.jpg"))
                .tags(List.of("rock", "outdoor"))
                .build();
    }

    // ── toProto ───────────────────────────────────────────────────────────────

    @Test
    void toProto_nullDocument_returnsNull() {
        assertThat(mapper.toProto(null)).isNull();
    }

    @Test
    void toProto_fullDocument_mapsAllScalarFields() {
        EventInfo proto = mapper.toProto(fullEvent());

        assertThat(proto.getId()).isEqualTo("ev-1");
        assertThat(proto.getTitle()).isEqualTo("Rock Fest");
        assertThat(proto.getDescription()).isEqualTo("A great festival");
        assertThat(proto.getCategory()).isEqualTo("CONCERT");
        assertThat(proto.getStatus()).isEqualTo("PUBLISHED");
        assertThat(proto.getTimezone()).isEqualTo("UTC");
    }

    @Test
    void toProto_fullDocument_mapsDateTimeFields() {
        EventInfo proto = mapper.toProto(fullEvent());

        assertThat(proto.getDateTime()).isEqualTo("2025-06-15T12:00:00Z");
        assertThat(proto.getEndDateTime()).isEqualTo("2025-06-15T14:00:00Z");
        assertThat(proto.getCreatedAt()).isEqualTo("2025-06-15T12:00:00Z");
    }

    @Test
    void toProto_nullDateTimeFields_returnsEmptyString() {
        EventDocument doc = EventDocument.builder().id("ev-1").build();

        EventInfo proto = mapper.toProto(doc);

        assertThat(proto.getDateTime()).isEmpty();
        assertThat(proto.getEndDateTime()).isEmpty();
    }

    @Test
    void toProto_mapsVenue() {
        EventInfo proto = mapper.toProto(fullEvent());

        assertThat(proto.getVenue().getName()).isEqualTo("Arena");
        assertThat(proto.getVenue().getCity()).isEqualTo("Berlin");
        assertThat(proto.getVenue().getCountry()).isEqualTo("DE");
    }

    @Test
    void toProto_mapsOrganizer() {
        EventInfo proto = mapper.toProto(fullEvent());

        assertThat(proto.getOrganizer().getUserId()).isEqualTo("u-1");
        assertThat(proto.getOrganizer().getName()).isEqualTo("Alice");
        assertThat(proto.getOrganizer().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void toProto_mapsSeatCategories() {
        EventInfo proto = mapper.toProto(fullEvent());

        assertThat(proto.getSeatCategoriesCount()).isEqualTo(1);
        SeatCategoryInfo sc = proto.getSeatCategories(0);
        assertThat(sc.getName()).isEqualTo("VIP");
        assertThat(sc.getPrice()).isEqualTo(150.0);
        assertThat(sc.getCurrency()).isEqualTo("USD");
        assertThat(sc.getTotalSeats()).isEqualTo(50);
        assertThat(sc.getAvailableSeats()).isEqualTo(30);
    }

    @Test
    void toProto_mapsImagesAndTags() {
        EventInfo proto = mapper.toProto(fullEvent());

        assertThat(proto.getImagesList()).containsExactly("img1.jpg", "img2.jpg");
        assertThat(proto.getTagsList()).containsExactly("rock", "outdoor");
    }

    @Test
    void toProto_nullVenue_omitsVenueField() {
        EventDocument doc = fullEvent();
        doc.setVenue(null);

        EventInfo proto = mapper.toProto(doc);

        assertThat(proto.hasVenue()).isFalse();
    }

    @Test
    void toProto_nullOrganizer_omitsOrganizerField() {
        EventDocument doc = fullEvent();
        doc.setOrganizer(null);

        EventInfo proto = mapper.toProto(doc);

        assertThat(proto.hasOrganizer()).isFalse();
    }

    @Test
    void toProto_nullSeatCategories_omitsSeatCategories() {
        EventDocument doc = fullEvent();
        doc.setSeatCategories(null);

        EventInfo proto = mapper.toProto(doc);

        assertThat(proto.getSeatCategoriesCount()).isEqualTo(0);
    }

    // ── toProtoList ───────────────────────────────────────────────────────────

    @Test
    void toProtoList_null_returnsEmptyList() {
        assertThat(mapper.toProtoList(null)).isEmpty();
    }

    @Test
    void toProtoList_emptyList_returnsEmptyList() {
        assertThat(mapper.toProtoList(List.of())).isEmpty();
    }

    @Test
    void toProtoList_mapsEachDocument() {
        List<EventInfo> result = mapper.toProtoList(List.of(fullEvent(), fullEvent()));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("ev-1");
    }

    // ── toVenueProto ──────────────────────────────────────────────────────────

    @Test
    void toVenueProto_null_returnsDefaultInstance() {
        assertThat(mapper.toVenueProto(null)).isEqualTo(VenueInfo.getDefaultInstance());
    }

    @Test
    void toVenueProto_withOptionalFields_setsLatLongCapacity() {
        VenueInfo proto = mapper.toVenueProto(venue());

        assertThat(proto.getLatitude()).isEqualTo(52.52);
        assertThat(proto.getLongitude()).isEqualTo(13.40);
        assertThat(proto.getCapacity()).isEqualTo(5000);
    }

    @Test
    void toVenueProto_withoutOptionalFields_omitsLatLongCapacity() {
        com.booking.platform.event_service.document.VenueInfo minimal =
                com.booking.platform.event_service.document.VenueInfo.builder()
                        .name("Hall").city("Paris").country("FR").build();

        VenueInfo proto = mapper.toVenueProto(minimal);

        assertThat(proto.hasLatitude()).isFalse();
        assertThat(proto.hasLongitude()).isFalse();
        assertThat(proto.hasCapacity()).isFalse();
    }

    // ── toOrganizerProto ──────────────────────────────────────────────────────

    @Test
    void toOrganizerProto_null_returnsDefaultInstance() {
        assertThat(mapper.toOrganizerProto(null)).isEqualTo(OrganizerInfo.getDefaultInstance());
    }

    @Test
    void toOrganizerProto_mapsAllFields() {
        OrganizerInfo proto = mapper.toOrganizerProto(organizer());

        assertThat(proto.getUserId()).isEqualTo("u-1");
        assertThat(proto.getName()).isEqualTo("Alice");
        assertThat(proto.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void toOrganizerProto_nullFields_mapsToEmpty() {
        com.booking.platform.event_service.document.OrganizerInfo org =
                com.booking.platform.event_service.document.OrganizerInfo.builder().build();

        OrganizerInfo proto = mapper.toOrganizerProto(org);

        assertThat(proto.getUserId()).isEmpty();
        assertThat(proto.getName()).isEmpty();
        assertThat(proto.getEmail()).isEmpty();
    }

    // ── toSeatCategoryProto ───────────────────────────────────────────────────

    @Test
    void toSeatCategoryProto_null_returnsDefaultInstance() {
        assertThat(mapper.toSeatCategoryProto(null)).isEqualTo(SeatCategoryInfo.getDefaultInstance());
    }

    @Test
    void toSeatCategoryProto_mapsAllFields() {
        SeatCategoryInfo proto = mapper.toSeatCategoryProto(seatCategory());

        assertThat(proto.getName()).isEqualTo("VIP");
        assertThat(proto.getPrice()).isEqualTo(150.0);
        assertThat(proto.getCurrency()).isEqualTo("USD");
        assertThat(proto.getTotalSeats()).isEqualTo(50);
        assertThat(proto.getAvailableSeats()).isEqualTo(30);
    }

    @Test
    void toSeatCategoryProto_nullPriceAndSeats_defaultsToZero() {
        SeatCategory sc = SeatCategory.builder().name("Free").currency("USD").build();

        SeatCategoryInfo proto = mapper.toSeatCategoryProto(sc);

        assertThat(proto.getPrice()).isEqualTo(0.0);
        assertThat(proto.getTotalSeats()).isEqualTo(0);
        assertThat(proto.getAvailableSeats()).isEqualTo(0);
    }
}
