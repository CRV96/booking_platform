package com.booking.platform.ticket_service.messaging.consumer;

import com.booking.platform.common.events.BookingConfirmedEvent;
import com.booking.platform.common.events.KafkaTopics;
import com.booking.platform.ticket_service.document.TicketDocument;
import com.booking.platform.ticket_service.dto.TicketDTO;
import com.booking.platform.ticket_service.service.TicketService;
import com.booking.platform.common.logging.ApplicationLogger;
import com.booking.platform.common.logging.LogErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
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

        ApplicationLogger.logMessage(log, Level.DEBUG,
                "[BOOKING_CONFIRMED] bookingId='{}', eventId='{}', category='{}', qty={}, partition={}, offset={}",
                event.getBookingId(), event.getEventId(), event.getSeatCategory(), event.getQuantity(),
                record.partition(), record.offset());

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

            ApplicationLogger.logMessage(log, Level.DEBUG, "Generated {} tickets for booking '{}': {}",
                    tickets.size(), event.getBookingId(),
                    tickets.stream().map(TicketDocument::getTicketNumber).toList());

        } catch (Exception e) {
            ApplicationLogger.logMessage(log, Level.ERROR, LogErrorCode.TICKET_GENERATION_FAILED,
                    "Failed to generate tickets for booking '{}'", event.getBookingId(), e);

            throw e;  // re-throw to trigger retry + DLT
        }
    }
}
