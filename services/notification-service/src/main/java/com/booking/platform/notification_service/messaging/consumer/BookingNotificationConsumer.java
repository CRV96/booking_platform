package com.booking.platform.notification_service.messaging.consumer;

import com.booking.platform.common.events.BookingCancelledEvent;
import com.booking.platform.common.events.BookingConfirmedEvent;
import com.booking.platform.common.events.BookingCreatedEvent;
import com.booking.platform.common.events.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for booking-domain lifecycle messages.
 *
 * <p>Each method listens to a single booking topic and logs the incoming event.
 * Full email sending (booking confirmation, cancellation notices) will be wired
 * in P2-05 (Email Templates and Sending).
 *
 * <p>These are the highest-priority notifications from the user's perspective:
 * <ul>
 *   <li>Booking created → "Your booking is being processed"</li>
 *   <li>Booking confirmed → "Your booking is confirmed" + ticket info</li>
 *   <li>Booking cancelled → "Your booking was cancelled" + refund info</li>
 * </ul>
 */
@Slf4j
@Component
public class BookingNotificationConsumer {

    /**
     * Receives notification when a booking is first created (status: PENDING).
     * Future: send "booking received, payment processing" email.
     */
    @KafkaListener(
            topics = KafkaTopics.BOOKING_CREATED,
            containerFactory = "bookingCreatedListenerFactory"
    )
    public void onBookingCreated(ConsumerRecord<String, BookingCreatedEvent> record) {
        BookingCreatedEvent event = record.value();
        log.info("[BOOKING_CREATED] bookingId='{}', eventId='{}', userId='{}', category='{}', qty={}, total={} {} | partition={}, offset={}",
                event.getBookingId(),
                event.getEventId(),
                event.getUserId(),
                event.getSeatCategory(),
                event.getQuantity(),
                event.getTotalPrice(),
                event.getCurrency(),
                record.partition(),
                record.offset());
        // TODO P2-05: send "booking received" email to customer
    }

    /**
     * Receives notification when a booking is confirmed (payment succeeded).
     * Future: send booking confirmation email with ticket IDs and QR codes.
     */
    @KafkaListener(
            topics = KafkaTopics.BOOKING_CONFIRMED,
            containerFactory = "bookingConfirmedListenerFactory"
    )
    public void onBookingConfirmed(ConsumerRecord<String, BookingConfirmedEvent> record) {
        BookingConfirmedEvent event = record.value();
        log.info("[BOOKING_CONFIRMED] bookingId='{}', eventId='{}', userId='{}', tickets={} | partition={}, offset={}",
                event.getBookingId(),
                event.getEventId(),
                event.getUserId(),
                event.getTicketIdsList(),
                record.partition(),
                record.offset());
        // TODO P2-05: send booking confirmation email with ticket details
    }

    /**
     * Receives notification when a booking is cancelled (by user or system).
     * Future: send cancellation email with refund information.
     */
    @KafkaListener(
            topics = KafkaTopics.BOOKING_CANCELLED,
            containerFactory = "bookingCancelledListenerFactory"
    )
    public void onBookingCancelled(ConsumerRecord<String, BookingCancelledEvent> record) {
        BookingCancelledEvent event = record.value();
        log.info("[BOOKING_CANCELLED] bookingId='{}', eventId='{}', userId='{}', reason='{}' | partition={}, offset={}",
                event.getBookingId(),
                event.getEventId(),
                event.getUserId(),
                event.getReason(),
                record.partition(),
                record.offset());
        // TODO P2-05: send cancellation + refund notice email to customer
    }
}
