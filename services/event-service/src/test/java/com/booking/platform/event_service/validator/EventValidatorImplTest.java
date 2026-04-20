package com.booking.platform.event_service.validator;

import com.booking.platform.common.grpc.event.CreateEventRequest;
import com.booking.platform.common.grpc.event.SeatCategoryInfo;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.document.OrganizerInfo;
import com.booking.platform.event_service.document.SeatCategory;
import com.booking.platform.event_service.document.enums.EventCategory;
import com.booking.platform.event_service.document.enums.EventStatus;
import com.booking.platform.event_service.exception.ValidationException;
import com.booking.platform.event_service.validator.impl.EventValidatorImpl;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class EventValidatorImplTest {

    private final EventValidatorImpl validator = new EventValidatorImpl();

    private static final String FUTURE = Instant.now().plus(10, ChronoUnit.DAYS).toString();
    private static final String FAR_FUTURE = Instant.now().plus(20, ChronoUnit.DAYS).toString();
    private static final String PAST = Instant.now().minus(1, ChronoUnit.DAYS).toString();

    private SeatCategoryInfo validSeatCategory() {
        return SeatCategoryInfo.newBuilder()
                .setName("General").setPrice(50.0).setCurrency("USD").setTotalSeats(100)
                .build();
    }

    private CreateEventRequest.Builder validRequest() {
        return CreateEventRequest.newBuilder()
                .setTitle("Rock Fest")
                .setCategory("CONCERT")
                .setDateTime(FUTURE)
                .setTimezone("UTC")
                .setVenue(com.booking.platform.common.grpc.event.VenueInfo.newBuilder().setName("Arena").setCity("Berlin").setCountry("DE").build())
                .addSeatCategories(validSeatCategory());
    }

    // ── validateCreateRequest — happy path ────────────────────────────────────

    @Test
    void validateCreateRequest_validRequest_doesNotThrow() {
        assertThatNoException().isThrownBy(() -> validator.validateCreateRequest(validRequest().build()));
    }

    // ── title validation ──────────────────────────────────────────────────────

    @Test
    void validateCreateRequest_blankTitle_throwsValidation() {
        assertThatThrownBy(() -> validator.validateCreateRequest(validRequest().setTitle("  ").build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("title");
    }

    // ── category validation ───────────────────────────────────────────────────

    @Test
    void validateCreateRequest_blankCategory_throwsValidation() {
        assertThatThrownBy(() -> validator.validateCreateRequest(validRequest().setCategory("").build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("category");
    }

    @Test
    void validateCreateRequest_invalidCategory_throwsValidation() {
        assertThatThrownBy(() -> validator.validateCreateRequest(validRequest().setCategory("UNKNOWN_CAT").build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid event category");
    }

    @Test
    void validateCreateRequest_allValidCategories_accepted() {
        for (EventCategory cat : EventCategory.values()) {
            assertThatNoException().isThrownBy(
                    () -> validator.validateCreateRequest(validRequest().setCategory(cat.name()).build()));
        }
    }

    // ── dateTime validation ───────────────────────────────────────────────────

    @Test
    void validateCreateRequest_blankDateTime_throwsValidation() {
        assertThatThrownBy(() -> validator.validateCreateRequest(validRequest().setDateTime("").build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("dateTime");
    }

    @Test
    void validateCreateRequest_malformedDateTime_throwsValidation() {
        assertThatThrownBy(() -> validator.validateCreateRequest(validRequest().setDateTime("not-a-date").build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("dateTime");
    }

    @Test
    void validateCreateRequest_endDateTimeBeforeDateTime_throwsValidation() {
        assertThatThrownBy(() -> validator.validateCreateRequest(
                validRequest().setDateTime(FAR_FUTURE).setEndDateTime(FUTURE).build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("endDateTime");
    }

    @Test
    void validateCreateRequest_endDateTimeAfterDateTime_accepted() {
        assertThatNoException().isThrownBy(() -> validator.validateCreateRequest(
                validRequest().setDateTime(FUTURE).setEndDateTime(FAR_FUTURE).build()));
    }

    @Test
    void validateCreateRequest_noEndDateTime_accepted() {
        assertThatNoException().isThrownBy(() -> validator.validateCreateRequest(
                validRequest().setDateTime(FUTURE).build()));
    }

    // ── seat categories validation ────────────────────────────────────────────

    @Test
    void validateCreateRequest_noSeatCategories_throwsValidation() {
        CreateEventRequest request = CreateEventRequest.newBuilder()
                .setTitle("Fest").setCategory("CONCERT").setDateTime(FUTURE)
                .setTimezone("UTC")
                .build();
        assertThatThrownBy(() -> validator.validateCreateRequest(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("seat category");
    }

    @Test
    void validateCreateRequest_blankSeatCategoryName_throwsValidation() {
        SeatCategoryInfo bad = SeatCategoryInfo.newBuilder()
                .setName("").setPrice(10.0).setCurrency("USD").setTotalSeats(50).build();
        assertThatThrownBy(() -> validator.validateCreateRequest(
                validRequest().clearSeatCategories().addSeatCategories(bad).build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Seat category name");
    }

    @Test
    void validateCreateRequest_negativeSeatPrice_throwsValidation() {
        SeatCategoryInfo bad = SeatCategoryInfo.newBuilder()
                .setName("VIP").setPrice(-1.0).setCurrency("USD").setTotalSeats(50).build();
        assertThatThrownBy(() -> validator.validateCreateRequest(
                validRequest().clearSeatCategories().addSeatCategories(bad).build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("price");
    }

    @Test
    void validateCreateRequest_zeroTotalSeats_throwsValidation() {
        SeatCategoryInfo bad = SeatCategoryInfo.newBuilder()
                .setName("GA").setPrice(10.0).setCurrency("USD").setTotalSeats(0).build();
        assertThatThrownBy(() -> validator.validateCreateRequest(
                validRequest().clearSeatCategories().addSeatCategories(bad).build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("totalSeats");
    }

    @Test
    void validateCreateRequest_blankSeatCategoryCurrency_throwsValidation() {
        SeatCategoryInfo bad = SeatCategoryInfo.newBuilder()
                .setName("GA").setPrice(10.0).setCurrency("").setTotalSeats(50).build();
        assertThatThrownBy(() -> validator.validateCreateRequest(
                validRequest().clearSeatCategories().addSeatCategories(bad).build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void validateCreateRequest_zeroPriceIsAccepted() {
        SeatCategoryInfo free = SeatCategoryInfo.newBuilder()
                .setName("Free").setPrice(0.0).setCurrency("USD").setTotalSeats(100).build();
        assertThatNoException().isThrownBy(() -> validator.validateCreateRequest(
                validRequest().clearSeatCategories().addSeatCategories(free).build()));
    }

    // ── parseInstant ──────────────────────────────────────────────────────────

    @Test
    void parseInstant_validIso8601_returnsInstant() {
        Instant result = validator.parseInstant("2024-06-15T18:00:00Z", "dateTime");

        assertThat(result).isEqualTo(Instant.parse("2024-06-15T18:00:00Z"));
    }

    @Test
    void parseInstant_invalidFormat_throwsValidation() {
        assertThatThrownBy(() -> validator.parseInstant("2024-06-15", "dateTime"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("dateTime");
    }

    @Test
    void parseInstant_plainDate_throwsValidation() {
        assertThatThrownBy(() -> validator.parseInstant("not-a-date", "myField"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("myField");
    }

    // ── validateForPublish ────────────────────────────────────────────────────

    @Test
    void validateForPublish_completeEvent_doesNotThrow() {
        EventDocument event = fullyValidEvent();
        assertThatNoException().isThrownBy(() -> validator.validateForPublish(event));
    }

    @Test
    void validateForPublish_missingTitle_throwsValidation() {
        EventDocument event = fullyValidEvent();
        event.setTitle(null);

        assertThatThrownBy(() -> validator.validateForPublish(event))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("title");
    }

    @Test
    void validateForPublish_blankTitle_throwsValidation() {
        EventDocument event = fullyValidEvent();
        event.setTitle("   ");

        assertThatThrownBy(() -> validator.validateForPublish(event))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("title");
    }

    @Test
    void validateForPublish_nullVenue_throwsValidation() {
        EventDocument event = fullyValidEvent();
        event.setVenue(null);

        assertThatThrownBy(() -> validator.validateForPublish(event))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("venue");
    }

    @Test
    void validateForPublish_blankVenueName_throwsValidation() {
        EventDocument event = fullyValidEvent();
        event.getVenue().setName("");

        assertThatThrownBy(() -> validator.validateForPublish(event))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("venue");
    }

    @Test
    void validateForPublish_nullDateTime_throwsValidation() {
        EventDocument event = fullyValidEvent();
        event.setDateTime(null);

        assertThatThrownBy(() -> validator.validateForPublish(event))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("dateTime");
    }

    @Test
    void validateForPublish_pastDateTime_throwsValidation() {
        EventDocument event = fullyValidEvent();
        event.setDateTime(Instant.now().minus(1, ChronoUnit.DAYS));

        assertThatThrownBy(() -> validator.validateForPublish(event))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("past");
    }

    @Test
    void validateForPublish_emptySeatCategories_throwsValidation() {
        EventDocument event = fullyValidEvent();
        event.setSeatCategories(List.of());

        assertThatThrownBy(() -> validator.validateForPublish(event))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("seat category");
    }

    @Test
    void validateForPublish_nullSeatCategories_throwsValidation() {
        EventDocument event = fullyValidEvent();
        event.setSeatCategories(null);

        assertThatThrownBy(() -> validator.validateForPublish(event))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("seat category");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EventDocument fullyValidEvent() {
        return EventDocument.builder()
                .id("ev-1")
                .title("Valid Event")
                .category(EventCategory.CONCERT)
                .status(EventStatus.DRAFT)
                .dateTime(Instant.now().plus(10, ChronoUnit.DAYS))
                .timezone("UTC")
                .venue(com.booking.platform.event_service.document.VenueInfo.builder()
                        .name("Arena").city("Berlin").country("DE").build())
                .organizer(OrganizerInfo.builder().userId("u-1").name("Alice").email("a@x.com").build())
                .seatCategories(List.of(
                        SeatCategory.builder().name("GA").price(50.0).currency("USD")
                                .totalSeats(100).availableSeats(100).build()))
                .build();
    }
}
