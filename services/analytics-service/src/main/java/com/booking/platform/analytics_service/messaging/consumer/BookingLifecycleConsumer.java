package com.booking.platform.analytics_service.messaging.consumer;

import com.booking.platform.analytics_service.service.BookingAnalyticsProcessor;
import com.booking.platform.common.events.*;
import com.booking.platform.common.events.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for booking lifecycle topics.
 *
 * <p>Handles: BookingCreated, BookingConfirmed, BookingCancelled.
 * Each listener extracts fields from the proto message and delegates
 * to {@link BookingAnalyticsProcessor}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingLifecycleConsumer {

    private final BookingAnalyticsProcessor processor;

    @KafkaListener(
            topics = KafkaTopics.BOOKING_CREATED,
            containerFactory = "bookingCreatedListenerFactory"
    )
    public void onBookingCreated(ConsumerRecord<String, BookingCreatedEvent> record) {
        BookingCreatedEvent event = record.value();
        log.info("[BOOKING_CREATED] bookingId='{}', eventId='{}', totalPrice={} {} | partition={}, offset={}",
                event.getBookingId(), event.getEventId(),
                event.getTotalPrice(), event.getCurrency(),
                record.partition(), record.offset());

        processor.processBookingCreated(
                record.topic(), record.key(),
                event.getBookingId(), event.getEventId(),
                event.getTotalPrice(), event.getCurrency());
    }

    @KafkaListener(
            topics = KafkaTopics.BOOKING_CONFIRMED,
            containerFactory = "bookingConfirmedListenerFactory"
    )
    public void onBookingConfirmed(ConsumerRecord<String, BookingConfirmedEvent> record) {
        BookingConfirmedEvent event = record.value();
        log.info("[BOOKING_CONFIRMED] bookingId='{}', eventId='{}', totalPrice={} {} | partition={}, offset={}",
                event.getBookingId(), event.getEventId(),
                event.getTotalPrice(), event.getCurrency(),
                record.partition(), record.offset());

        processor.processBookingConfirmed(
                record.topic(), record.key(),
                event.getBookingId(), event.getEventId(),
                event.getTotalPrice(), event.getCurrency(),
                event.getEventTitle(), event.getSeatCategory());
    }

    @KafkaListener(
            topics = KafkaTopics.BOOKING_CANCELLED,
            containerFactory = "bookingCancelledListenerFactory"
    )
    public void onBookingCancelled(ConsumerRecord<String, BookingCancelledEvent> record) {
        BookingCancelledEvent event = record.value();
        log.info("[BOOKING_CANCELLED] bookingId='{}', eventId='{}', reason='{}' | partition={}, offset={}",
                event.getBookingId(), event.getEventId(), event.getReason(),
                record.partition(), record.offset());

        processor.processBookingCancelled(
                record.topic(), record.key(),
                event.getBookingId(), event.getEventId());
    }
}
