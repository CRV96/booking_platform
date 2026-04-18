package com.booking.platform.ticket_service.validation;

import com.booking.platform.ticket_service.dto.TicketDTO;
import com.booking.platform.ticket_service.exception.InvalidTicketOperationException;
import com.booking.platform.ticket_service.properties.TicketProperties;
import com.booking.platform.ticket_service.validation.impl.BookingValidationImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingValidationImplTest {

    private BookingValidationImpl validator;

    @BeforeEach
    void setUp() {
        validator = new BookingValidationImpl(new TicketProperties(20));
    }

    private TicketDTO valid() {
        return TicketDTO.builder()
                .bookingId("booking-1")
                .eventId("event-1")
                .userId("user-1")
                .seatCategory("VIP")
                .eventTitle("Concert")
                .quantity(2)
                .build();
    }

    // ── validateTicketRequest ─────────────────────────────────────────────────

    @Test
    void validateTicketRequest_valid_doesNotThrow() {
        assertThatCode(() -> validator.validateTicketRequest(valid())).doesNotThrowAnyException();
    }

    @Test
    void validateTicketRequest_null_throws() {
        assertThatThrownBy(() -> validator.validateTicketRequest(null))
                .isInstanceOf(InvalidTicketOperationException.class);
    }

    @Test
    void validateTicketRequest_nullBookingId_throws() {
        TicketDTO dto = TicketDTO.builder().bookingId(null).eventId("e").userId("u")
                .seatCategory("VIP").eventTitle("Concert").quantity(1).build();
        assertThatThrownBy(() -> validator.validateTicketRequest(dto))
                .isInstanceOf(InvalidTicketOperationException.class)
                .hasMessageContaining("bookingId");
    }

    @Test
    void validateTicketRequest_blankBookingId_throws() {
        TicketDTO dto = TicketDTO.builder().bookingId("  ").eventId("e").userId("u")
                .seatCategory("VIP").eventTitle("Concert").quantity(1).build();
        assertThatThrownBy(() -> validator.validateTicketRequest(dto))
                .isInstanceOf(InvalidTicketOperationException.class)
                .hasMessageContaining("bookingId");
    }

    @Test
    void validateTicketRequest_nullEventId_throws() {
        TicketDTO dto = TicketDTO.builder().bookingId("b").eventId(null).userId("u")
                .seatCategory("VIP").eventTitle("Concert").quantity(1).build();
        assertThatThrownBy(() -> validator.validateTicketRequest(dto))
                .isInstanceOf(InvalidTicketOperationException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void validateTicketRequest_nullUserId_throws() {
        TicketDTO dto = TicketDTO.builder().bookingId("b").eventId("e").userId(null)
                .seatCategory("VIP").eventTitle("Concert").quantity(1).build();
        assertThatThrownBy(() -> validator.validateTicketRequest(dto))
                .isInstanceOf(InvalidTicketOperationException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void validateTicketRequest_nullSeatCategory_throws() {
        TicketDTO dto = TicketDTO.builder().bookingId("b").eventId("e").userId("u")
                .seatCategory(null).eventTitle("Concert").quantity(1).build();
        assertThatThrownBy(() -> validator.validateTicketRequest(dto))
                .isInstanceOf(InvalidTicketOperationException.class)
                .hasMessageContaining("seatCategory");
    }

    @Test
    void validateTicketRequest_blankEventTitle_throws() {
        TicketDTO dto = TicketDTO.builder().bookingId("b").eventId("e").userId("u")
                .seatCategory("VIP").eventTitle("").quantity(1).build();
        assertThatThrownBy(() -> validator.validateTicketRequest(dto))
                .isInstanceOf(InvalidTicketOperationException.class)
                .hasMessageContaining("eventTitle");
    }

    @Test
    void validateTicketRequest_zeroQuantity_throws() {
        TicketDTO dto = TicketDTO.builder().bookingId("b").eventId("e").userId("u")
                .seatCategory("VIP").eventTitle("Concert").quantity(0).build();
        assertThatThrownBy(() -> validator.validateTicketRequest(dto))
                .isInstanceOf(InvalidTicketOperationException.class)
                .hasMessageContaining("1");
    }

    @Test
    void validateTicketRequest_negativeQuantity_throws() {
        TicketDTO dto = TicketDTO.builder().bookingId("b").eventId("e").userId("u")
                .seatCategory("VIP").eventTitle("Concert").quantity(-1).build();
        assertThatThrownBy(() -> validator.validateTicketRequest(dto))
                .isInstanceOf(InvalidTicketOperationException.class);
    }

    @Test
    void validateTicketRequest_quantityExceedsMax_throws() {
        TicketDTO dto = TicketDTO.builder().bookingId("b").eventId("e").userId("u")
                .seatCategory("VIP").eventTitle("Concert").quantity(21).build();
        assertThatThrownBy(() -> validator.validateTicketRequest(dto))
                .isInstanceOf(InvalidTicketOperationException.class)
                .hasMessageContaining("20");
    }

    @Test
    void validateTicketRequest_quantityAtMax_doesNotThrow() {
        TicketDTO dto = TicketDTO.builder().bookingId("b").eventId("e").userId("u")
                .seatCategory("VIP").eventTitle("Concert").quantity(20).build();
        assertThatCode(() -> validator.validateTicketRequest(dto)).doesNotThrowAnyException();
    }

    @Test
    void validateTicketRequest_customMax_enforcedCorrectly() {
        BookingValidationImpl strictValidator = new BookingValidationImpl(new TicketProperties(5));
        TicketDTO tooMany = TicketDTO.builder().bookingId("b").eventId("e").userId("u")
                .seatCategory("VIP").eventTitle("Concert").quantity(6).build();
        assertThatThrownBy(() -> strictValidator.validateTicketRequest(tooMany))
                .isInstanceOf(InvalidTicketOperationException.class)
                .hasMessageContaining("5");
    }

    // ── validateBookingId ─────────────────────────────────────────────────────

    @Test
    void validateBookingId_valid_doesNotThrow() {
        assertThatCode(() -> validator.validateBookingId("booking-1")).doesNotThrowAnyException();
    }

    @Test
    void validateBookingId_null_throws() {
        assertThatThrownBy(() -> validator.validateBookingId(null))
                .isInstanceOf(InvalidTicketOperationException.class)
                .hasMessageContaining("bookingId");
    }

    @Test
    void validateBookingId_blank_throws() {
        assertThatThrownBy(() -> validator.validateBookingId("  "))
                .isInstanceOf(InvalidTicketOperationException.class)
                .hasMessageContaining("bookingId");
    }

    // ── validateTicketNumber ──────────────────────────────────────────────────

    @Test
    void validateTicketNumber_valid_doesNotThrow() {
        assertThatCode(() -> validator.validateTicketNumber("TKT-20240101-ABCDEF")).doesNotThrowAnyException();
    }

    @Test
    void validateTicketNumber_null_throws() {
        assertThatThrownBy(() -> validator.validateTicketNumber(null))
                .isInstanceOf(InvalidTicketOperationException.class)
                .hasMessageContaining("ticketNumber");
    }

    @Test
    void validateTicketNumber_blank_throws() {
        assertThatThrownBy(() -> validator.validateTicketNumber(""))
                .isInstanceOf(InvalidTicketOperationException.class)
                .hasMessageContaining("ticketNumber");
    }
}
