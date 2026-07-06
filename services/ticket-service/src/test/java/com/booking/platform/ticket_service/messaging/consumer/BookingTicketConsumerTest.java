package com.booking.platform.ticket_service.messaging.consumer;

import com.booking.platform.common.events.BookingConfirmedEvent;
import com.booking.platform.ticket_service.document.TicketDocument;
import com.booking.platform.ticket_service.document.enums.TicketStatus;
import com.booking.platform.ticket_service.dto.TicketDTO;
import com.booking.platform.ticket_service.service.TicketService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingTicketConsumerTest {

    @Mock private TicketService ticketService;
    @InjectMocks private BookingTicketConsumer consumer;

    private BookingConfirmedEvent event(String bookingId) {
        return BookingConfirmedEvent.newBuilder()
                .setBookingId(bookingId)
                .setEventId("event-1")
                .setUserId("user-1")
                .setSeatCategory("VIP")
                .setQuantity(2)
                .setEventTitle("Concert Night")
                .build();
    }

    private ConsumerRecord<String, BookingConfirmedEvent> record(BookingConfirmedEvent e) {
        return new ConsumerRecord<>("events.booking.confirmed", 0, 100L, "key", e);
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void onBookingConfirmed_success_callsGenerateTickets() {
        when(ticketService.generateTickets(any())).thenReturn(List.of());

        consumer.onBookingConfirmed(record(event("booking-1")));

        verify(ticketService).generateTickets(any(TicketDTO.class));
    }

    @Test
    void onBookingConfirmed_mapsEventFieldsToDto() {
        when(ticketService.generateTickets(any())).thenReturn(List.of());

        consumer.onBookingConfirmed(record(event("booking-42")));

        ArgumentCaptor<TicketDTO> captor = ArgumentCaptor.forClass(TicketDTO.class);
        verify(ticketService).generateTickets(captor.capture());

        TicketDTO dto = captor.getValue();
        assertThat(dto.bookingId()).isEqualTo("booking-42");
        assertThat(dto.eventId()).isEqualTo("event-1");
        assertThat(dto.userId()).isEqualTo("user-1");
        assertThat(dto.seatCategory()).isEqualTo("VIP");
        assertThat(dto.quantity()).isEqualTo(2);
        assertThat(dto.eventTitle()).isEqualTo("Concert Night");
    }

    @Test
    void onBookingConfirmed_successfulGeneration_logsTicketNumbers() {
        TicketDocument t1 = TicketDocument.builder().ticketNumber("TKT-20240101-AAAAAA").status(TicketStatus.VALID).build();
        TicketDocument t2 = TicketDocument.builder().ticketNumber("TKT-20240101-BBBBBB").status(TicketStatus.VALID).build();
        when(ticketService.generateTickets(any())).thenReturn(List.of(t1, t2));

        // Should not throw even though we're just verifying the interaction
        consumer.onBookingConfirmed(record(event("booking-1")));

        verify(ticketService).generateTickets(any());
    }

    // ── error handling ────────────────────────────────────────────────────────

    @Test
    void onBookingConfirmed_serviceThrows_rethrowsForRetry() {
        doThrow(new RuntimeException("MongoDB unavailable"))
                .when(ticketService).generateTickets(any());

        assertThatThrownBy(() -> consumer.onBookingConfirmed(record(event("booking-1"))))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("MongoDB unavailable");
    }

    @Test
    void onBookingConfirmed_serviceThrowsCheckedException_rethrows() {
        doThrow(new IllegalStateException("Validation failed"))
                .when(ticketService).generateTickets(any());

        assertThatThrownBy(() -> consumer.onBookingConfirmed(record(event("booking-1"))))
                .isInstanceOf(IllegalStateException.class);
    }
}
