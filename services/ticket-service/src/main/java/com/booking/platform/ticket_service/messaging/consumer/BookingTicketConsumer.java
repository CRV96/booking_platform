package com.booking.platform.ticket_service.messaging.consumer;

import com.booking.platform.common.events.BookingConfirmedEvent;
import com.booking.platform.common.events.KafkaTopics;
import com.booking.platform.ticket_service.document.TicketDocument;
import com.booking.platform.ticket_service.dto.TicketDTO;
import com.booking.platform.ticket_service.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Kafka consumer for booking confirmation events — generates tickets.
 *
 * <p>When a booking is confirmed (payment succeeded), this consumer generates
 * the requested number of tickets and persists them to MongoDB.
 *
 * <p>The generated tickets are available for query via the ticket-service API
 * and will eventually be sent to the user in the confirmation email.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingTicketConsumer {

    private final TicketService ticketService;

    @KafkaListener(
            topics = KafkaTopics.BOOKING_CONFIRMED,
            containerFactory = "bookingConfirmedListenerFactory"
    )
    public void onBookingConfirmed(ConsumerRecord<String, BookingConfirmedEvent> record) {
        BookingConfirmedEvent event = record.value();

        log.debug("[BOOKING_CONFIRMED] bookingId='{}', eventId='{}', category='{}', qty={}",
                event.getBookingId(), event.getEventId(), event.getSeatCategory(), event.getQuantity());

        try {
            List<TicketDocument> tickets = ticketService.generateTickets(
                    TicketDTO.builder()
                            .bookingId(event.getBookingId())
                            .eventId(event.getEventId())
                            .userId(event.getUserId())
                            .seatCategory(event.getSeatCategory())
                            .quantity(event.getQuantity())
                            .eventTitle(event.getEventTitle())
                            .build()
            );

            log.debug("Generated {} tickets for booking '{}': {}",
                    tickets.size(),
                    event.getBookingId(),
                    tickets.stream().map(TicketDocument::getTicketNumber).toList());

        } catch (Exception e) {
            log.error("Failed to generate tickets for booking '{}': {}",
                    event.getBookingId(), e.getMessage());

            throw e;  // re-throw to trigger retry + DLT
        }
    }
}
