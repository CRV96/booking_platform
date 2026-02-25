package com.booking.platform.booking_service.messaging.consumer;

import com.booking.platform.booking_service.service.BookingService;
import com.booking.platform.common.events.KafkaTopics;
import com.booking.platform.common.events.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka consumer for payment-domain lifecycle messages.
 *
 * <p>Listens to {@code events.payment.completed} and triggers booking confirmation.
 * When a {@link PaymentCompletedEvent} arrives, it calls
 * {@link BookingService#confirmBooking(UUID)} which transitions the booking
 * from PENDING → CONFIRMED and publishes a {@code BookingConfirmedEvent}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final BookingService bookingService;

    /**
     * Handles successful payment events by confirming the associated booking.
     *
     * <p>On {@code PaymentCompletedEvent}:
     * <ol>
     *   <li>Extract the bookingId from the event</li>
     *   <li>Call {@code bookingService.confirmBooking(bookingId)}</li>
     *   <li>confirmBooking internally publishes {@code BookingConfirmedEvent}</li>
     * </ol>
     */
    @KafkaListener(
            topics = KafkaTopics.PAYMENT_COMPLETED,
            containerFactory = "paymentCompletedListenerFactory"
    )
    public void onPaymentCompleted(ConsumerRecord<String, PaymentCompletedEvent> record) {
        PaymentCompletedEvent event = record.value();
        log.info("[PAYMENT_COMPLETED] paymentId='{}', bookingId='{}', amount={} {} | partition={}, offset={}",
                event.getPaymentId(),
                event.getBookingId(),
                event.getAmount(),
                event.getCurrency(),
                record.partition(),
                record.offset());

        try {
            UUID bookingId = UUID.fromString(event.getBookingId());
            bookingService.confirmBooking(bookingId);
            log.info("Booking confirmed via payment: bookingId='{}'", event.getBookingId());
        } catch (Exception e) {
            log.error("Failed to confirm booking '{}' from payment '{}': {}",
                    event.getBookingId(), event.getPaymentId(), e.getMessage());
            throw e;  // re-throw to trigger retry + DLT
        }
    }
}
