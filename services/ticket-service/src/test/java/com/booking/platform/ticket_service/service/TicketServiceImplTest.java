package com.booking.platform.ticket_service.service;

import com.booking.platform.ticket_service.document.TicketDocument;
import com.booking.platform.ticket_service.document.enums.TicketStatus;
import com.booking.platform.ticket_service.dto.TicketDTO;
import com.booking.platform.ticket_service.exception.TicketAlreadyUsedException;
import com.booking.platform.ticket_service.exception.TicketCancelledException;
import com.booking.platform.ticket_service.exception.TicketNotFoundException;
import com.booking.platform.ticket_service.repository.TicketRepository;
import com.booking.platform.ticket_service.service.impl.TicketServiceImpl;
import com.booking.platform.ticket_service.validation.BookingValidation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private BookingValidation bookingValidation;

    @InjectMocks private TicketServiceImpl ticketService;

    private TicketDTO validDto(int quantity) {
        return TicketDTO.builder()
                .bookingId("booking-1")
                .eventId("event-1")
                .userId("user-1")
                .seatCategory("VIP")
                .eventTitle("Concert")
                .quantity(quantity)
                .build();
    }

    private TicketDocument ticket(String number, TicketStatus status) {
        return TicketDocument.builder()
                .id("id-1")
                .ticketNumber(number)
                .status(status)
                .build();
    }

    // ── generateTickets ───────────────────────────────────────────────────────

    @Test
    void generateTickets_noExisting_savesAndReturnsNewTickets() {
        when(ticketRepository.findByBookingId("booking-1")).thenReturn(List.of());
        List<TicketDocument> saved = List.of(
                ticket("TKT-20240101-AA1111", TicketStatus.VALID),
                ticket("TKT-20240101-BB2222", TicketStatus.VALID));
        when(ticketRepository.saveAll(anyList())).thenReturn(saved);

        List<TicketDocument> result = ticketService.generateTickets(validDto(2));

        assertThat(result).hasSize(2);
        verify(ticketRepository).saveAll(anyList());
    }

    @Test
    void generateTickets_existingTickets_returnsExistingWithoutSaving() {
        List<TicketDocument> existing = List.of(ticket("TKT-20240101-AA1111", TicketStatus.VALID));
        when(ticketRepository.findByBookingId("booking-1")).thenReturn(existing);

        List<TicketDocument> result = ticketService.generateTickets(validDto(1));

        assertThat(result).isSameAs(existing);
        verify(ticketRepository, never()).saveAll(anyList());
    }

    @Test
    void generateTickets_createsCorrectCount() {
        when(ticketRepository.findByBookingId(anyString())).thenReturn(List.of());
        when(ticketRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ticketService.generateTickets(validDto(3));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TicketDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(3);
    }

    @Test
    void generateTickets_ticketsHaveValidInitialStatus() {
        when(ticketRepository.findByBookingId(anyString())).thenReturn(List.of());
        when(ticketRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ticketService.generateTickets(validDto(2));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TicketDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).allMatch(t -> t.getStatus() == TicketStatus.VALID);
    }

    @Test
    void generateTickets_ticketNumberMatchesFormat() {
        when(ticketRepository.findByBookingId(anyString())).thenReturn(List.of());
        when(ticketRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ticketService.generateTickets(validDto(1));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TicketDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAll(captor.capture());
        String ticketNumber = captor.getValue().get(0).getTicketNumber();
        assertThat(ticketNumber).matches("TKT-\\d{8}-[A-Z0-9]{6}");
    }

    @Test
    void generateTickets_qrCodeDataIsUuid() {
        when(ticketRepository.findByBookingId(anyString())).thenReturn(List.of());
        when(ticketRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ticketService.generateTickets(validDto(1));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TicketDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAll(captor.capture());
        String qrCode = captor.getValue().get(0).getQrCodeData();
        assertThat(qrCode).matches("[0-9a-f-]{36}");
    }

    @Test
    void generateTickets_populatesBookingAndEventAndUser() {
        when(ticketRepository.findByBookingId(anyString())).thenReturn(List.of());
        when(ticketRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ticketService.generateTickets(validDto(1));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TicketDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAll(captor.capture());
        TicketDocument t = captor.getValue().get(0);
        assertThat(t.getBookingId()).isEqualTo("booking-1");
        assertThat(t.getEventId()).isEqualTo("event-1");
        assertThat(t.getUserId()).isEqualTo("user-1");
        assertThat(t.getSeatCategory()).isEqualTo("VIP");
        assertThat(t.getEventTitle()).isEqualTo("Concert");
    }

    @Test
    void generateTickets_callsValidation() {
        when(ticketRepository.findByBookingId(anyString())).thenReturn(List.of());
        when(ticketRepository.saveAll(anyList())).thenReturn(List.of());
        TicketDTO dto = validDto(1);

        ticketService.generateTickets(dto);

        verify(bookingValidation).validateTicketRequest(dto);
    }

    // ── getTicketsByBooking ───────────────────────────────────────────────────

    @Test
    void getTicketsByBooking_delegatesToRepository() {
        List<TicketDocument> tickets = List.of(ticket("TKT-A", TicketStatus.VALID));
        when(ticketRepository.findByBookingId("booking-1")).thenReturn(tickets);

        List<TicketDocument> result = ticketService.getTicketsByBooking("booking-1");

        assertThat(result).isSameAs(tickets);
        verify(bookingValidation).validateBookingId("booking-1");
    }

    @Test
    void getTicketsByBooking_noTickets_returnsEmptyList() {
        when(ticketRepository.findByBookingId("booking-1")).thenReturn(List.of());

        assertThat(ticketService.getTicketsByBooking("booking-1")).isEmpty();
    }

    // ── getByTicketNumber ─────────────────────────────────────────────────────

    @Test
    void getByTicketNumber_found_returnsTicket() {
        TicketDocument t = ticket("TKT-20240101-ABCDEF", TicketStatus.VALID);
        when(ticketRepository.findByTicketNumber("TKT-20240101-ABCDEF")).thenReturn(Optional.of(t));

        assertThat(ticketService.getByTicketNumber("TKT-20240101-ABCDEF")).isSameAs(t);
    }

    @Test
    void getByTicketNumber_notFound_throwsTicketNotFoundException() {
        when(ticketRepository.findByTicketNumber("TKT-MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.getByTicketNumber("TKT-MISSING"))
                .isInstanceOf(TicketNotFoundException.class)
                .hasMessageContaining("TKT-MISSING");
    }

    // ── getTicketsByUserId ────────────────────────────────────────────────────

    @Test
    void getTicketsByUserId_list_delegatesToRepository() {
        List<TicketDocument> tickets = List.of(ticket("TKT-A", TicketStatus.VALID));
        when(ticketRepository.findByUserId("user-1")).thenReturn(tickets);

        assertThat(ticketService.getTicketsByUserId("user-1")).isSameAs(tickets);
    }

    @Test
    void getTicketsByUserId_paginated_delegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<TicketDocument> page = new PageImpl<>(List.of(ticket("TKT-A", TicketStatus.VALID)));
        when(ticketRepository.findByUserId("user-1", pageable)).thenReturn(page);

        assertThat(ticketService.getTicketsByUserId("user-1", pageable)).isSameAs(page);
    }

    // ── validateTicket ────────────────────────────────────────────────────────

    @Test
    void validateTicket_validStatus_marksAsUsed() {
        TicketDocument t = ticket("TKT-A", TicketStatus.VALID);
        when(ticketRepository.findByTicketNumber("TKT-A")).thenReturn(Optional.of(t));
        when(ticketRepository.save(t)).thenReturn(t);

        TicketDocument result = ticketService.validateTicket("TKT-A");

        assertThat(result.getStatus()).isEqualTo(TicketStatus.USED);
        verify(ticketRepository).save(t);
    }

    @Test
    void validateTicket_expiredStatus_marksAsUsed() {
        TicketDocument t = ticket("TKT-A", TicketStatus.EXPIRED);
        when(ticketRepository.findByTicketNumber("TKT-A")).thenReturn(Optional.of(t));
        when(ticketRepository.save(t)).thenReturn(t);

        TicketDocument result = ticketService.validateTicket("TKT-A");

        assertThat(result.getStatus()).isEqualTo(TicketStatus.USED);
    }

    @Test
    void validateTicket_alreadyUsed_throwsTicketAlreadyUsedException() {
        when(ticketRepository.findByTicketNumber("TKT-A"))
                .thenReturn(Optional.of(ticket("TKT-A", TicketStatus.USED)));

        assertThatThrownBy(() -> ticketService.validateTicket("TKT-A"))
                .isInstanceOf(TicketAlreadyUsedException.class)
                .hasMessageContaining("TKT-A");
    }

    @Test
    void validateTicket_cancelled_throwsTicketCancelledException() {
        when(ticketRepository.findByTicketNumber("TKT-A"))
                .thenReturn(Optional.of(ticket("TKT-A", TicketStatus.CANCELLED)));

        assertThatThrownBy(() -> ticketService.validateTicket("TKT-A"))
                .isInstanceOf(TicketCancelledException.class)
                .hasMessageContaining("TKT-A");
    }

    @Test
    void validateTicket_notFound_throwsTicketNotFoundException() {
        when(ticketRepository.findByTicketNumber("TKT-MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.validateTicket("TKT-MISSING"))
                .isInstanceOf(TicketNotFoundException.class)
                .hasMessageContaining("TKT-MISSING");
    }

    @Test
    void validateTicket_callsValidation() {
        when(ticketRepository.findByTicketNumber("TKT-A"))
                .thenReturn(Optional.of(ticket("TKT-A", TicketStatus.VALID)));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ticketService.validateTicket("TKT-A");

        verify(bookingValidation).validateTicketNumber("TKT-A");
    }

    // ── cancelTicket ──────────────────────────────────────────────────────────

    @Test
    void cancelTicket_validStatus_marksAsCancelled() {
        TicketDocument t = ticket("TKT-A", TicketStatus.VALID);
        when(ticketRepository.findByTicketNumber("TKT-A")).thenReturn(Optional.of(t));
        when(ticketRepository.save(t)).thenReturn(t);

        TicketDocument result = ticketService.cancelTicket("TKT-A");

        assertThat(result.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        verify(ticketRepository).save(t);
    }

    @Test
    void cancelTicket_expiredStatus_marksAsCancelled() {
        TicketDocument t = ticket("TKT-A", TicketStatus.EXPIRED);
        when(ticketRepository.findByTicketNumber("TKT-A")).thenReturn(Optional.of(t));
        when(ticketRepository.save(t)).thenReturn(t);

        TicketDocument result = ticketService.cancelTicket("TKT-A");

        assertThat(result.getStatus()).isEqualTo(TicketStatus.CANCELLED);
    }

    @Test
    void cancelTicket_alreadyCancelled_returnsIdempotently() {
        TicketDocument t = ticket("TKT-A", TicketStatus.CANCELLED);
        when(ticketRepository.findByTicketNumber("TKT-A")).thenReturn(Optional.of(t));

        TicketDocument result = ticketService.cancelTicket("TKT-A");

        assertThat(result).isSameAs(t);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void cancelTicket_alreadyUsed_throwsTicketAlreadyUsedException() {
        when(ticketRepository.findByTicketNumber("TKT-A"))
                .thenReturn(Optional.of(ticket("TKT-A", TicketStatus.USED)));

        assertThatThrownBy(() -> ticketService.cancelTicket("TKT-A"))
                .isInstanceOf(TicketAlreadyUsedException.class)
                .hasMessageContaining("TKT-A");
    }

    @Test
    void cancelTicket_notFound_throwsTicketNotFoundException() {
        when(ticketRepository.findByTicketNumber("TKT-MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.cancelTicket("TKT-MISSING"))
                .isInstanceOf(TicketNotFoundException.class)
                .hasMessageContaining("TKT-MISSING");
    }

    @Test
    void cancelTicket_callsValidation() {
        when(ticketRepository.findByTicketNumber("TKT-A"))
                .thenReturn(Optional.of(ticket("TKT-A", TicketStatus.VALID)));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ticketService.cancelTicket("TKT-A");

        verify(bookingValidation).validateTicketNumber("TKT-A");
    }
}
