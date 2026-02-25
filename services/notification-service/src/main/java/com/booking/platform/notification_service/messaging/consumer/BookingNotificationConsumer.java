package com.booking.platform.notification_service.messaging.consumer;

import com.booking.platform.common.events.BookingCancelledEvent;
import com.booking.platform.common.events.BookingConfirmedEvent;
import com.booking.platform.common.events.BookingCreatedEvent;
import com.booking.platform.common.events.KafkaTopics;
import com.booking.platform.notification_service.email.EmailService;
import com.booking.platform.notification_service.constants.EmailTemplatesConst;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kafka consumer for booking-domain lifecycle messages.
 *
 * <p>Listens to the three booking topics and triggers email notifications
 * for each state transition in the booking lifecycle:
 * <ul>
 *   <li>{@code BOOKING_CREATED}   → log only (no email — user sees UI feedback immediately)</li>
 *   <li>{@code BOOKING_CONFIRMED} → sends a confirmation email with ticket IDs</li>
 *   <li>{@code BOOKING_CANCELLED} → sends a cancellation email with refund information</li>
 * </ul>
 *
 * <p>The recipient email is derived from the userId as a placeholder because
 * the user's real email is owned by user-service (not yet integrated here).
 * In P3+, this will be replaced by a gRPC lookup to user-service, or the email
 * will be denormalized into the Protobuf event payload.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingNotificationConsumer {

    private final EmailService emailService;

    /**
     * Receives notification when a booking is first created (status: PENDING).
     * No email sent here — user already sees immediate UI feedback,
     * and payment has not yet completed so confirmation would be premature.
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
        // No email — wait for BOOKING_CONFIRMED (payment success) before notifying user.
    }

    /**
     * Sends a booking confirmation email when payment succeeds and the booking
     * transitions to CONFIRMED status.
     *
     * <p>Recipient address is stubbed from userId. In production, fetch the real
     * email from user-service via gRPC.
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

        // TODO P3+: replace stub with real email from user-service gRPC lookup
        String recipientEmail = "user-" + event.getUserId() + "@booking-platform.dev";

        emailService.sendHtml(
                recipientEmail,
                EmailTemplatesConst.BookingConfirmation.SUBJECT,
                EmailTemplatesConst.BookingConfirmation.TEMPLATE,
                Map.of(
                        EmailTemplatesConst.BookingConfirmation.Vars.BOOKING_ID,    event.getBookingId(),
                        EmailTemplatesConst.BookingConfirmation.Vars.EVENT_ID,      event.getEventId(),
                        EmailTemplatesConst.BookingConfirmation.Vars.TICKET_IDS,    event.getTicketIdsList(),
                        EmailTemplatesConst.BookingConfirmation.Vars.TIMESTAMP,     event.getTimestamp(),
                        EmailTemplatesConst.BookingConfirmation.Vars.SEAT_CATEGORY, event.getSeatCategory(),
                        EmailTemplatesConst.BookingConfirmation.Vars.QUANTITY,      String.valueOf(event.getQuantity()),
                        EmailTemplatesConst.BookingConfirmation.Vars.TOTAL_PRICE,   String.valueOf(event.getTotalPrice()),
                        EmailTemplatesConst.BookingConfirmation.Vars.CURRENCY,      event.getCurrency()
                )
        );
    }

    /**
     * Sends a cancellation + refund notice email when a booking is cancelled
     * by the user or by the system (e.g. payment timeout, hold expired).
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

        // TODO P3+: replace stub with real email from user-service gRPC lookup
        String recipientEmail = "user-" + event.getUserId() + "@booking-platform.dev";

        emailService.sendHtml(
                recipientEmail,
                EmailTemplatesConst.BookingCancellation.SUBJECT,
                EmailTemplatesConst.BookingCancellation.TEMPLATE,
                Map.of(
                        EmailTemplatesConst.BookingCancellation.Vars.BOOKING_ID, event.getBookingId(),
                        EmailTemplatesConst.BookingCancellation.Vars.EVENT_ID,   event.getEventId(),
                        EmailTemplatesConst.BookingCancellation.Vars.REASON,     event.getReason(),
                        EmailTemplatesConst.BookingCancellation.Vars.TIMESTAMP,  event.getTimestamp()
                )
        );
    }
}
