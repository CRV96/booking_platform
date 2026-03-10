package com.booking.platform.analytics_service.messaging.consumer;

import com.booking.platform.analytics_service.constants.BkgAnalyticsConstants;
import com.booking.platform.analytics_service.dto.BookingDto;
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
            containerFactory = BkgAnalyticsConstants.BkgBookingConstants.BOOKING_CREATED_FACTORY
    )
    public void onBookingCreated(ConsumerRecord<String, BookingCreatedEvent> record) {
        BookingCreatedEvent event = record.value();

        log.info("[BOOKING_CREATED] bookingId='{}', eventId='{}', totalPrice={} {} | partition={}, offset={}",
                event.getBookingId(), event.getEventId(),
                event.getTotalPrice(), event.getCurrency(),
                record.partition(), record.offset());

        processor.processBookingCreated(
                BookingDto.builder()
                        .topic(record.topic())
                        .key(record.key())
                        .bookingId(event.getBookingId())
                        .eventId(event.getEventId())
                        .totalPrice(event.getTotalPrice())
                        .currency(event.getCurrency())
                        .build());
    }

    @KafkaListener(
            topics = KafkaTopics.BOOKING_CONFIRMED,
            containerFactory = BkgAnalyticsConstants.BkgBookingConstants.BOOKING_CONFIRMED_FACTORY
    )
    public void onBookingConfirmed(ConsumerRecord<String, BookingConfirmedEvent> record) {
        BookingConfirmedEvent event = record.value();

        log.info("[BOOKING_CONFIRMED] bookingId='{}', eventId='{}', totalPrice={} {} | partition={}, offset={}",
                event.getBookingId(), event.getEventId(),
                event.getTotalPrice(), event.getCurrency(),
                record.partition(), record.offset());

        processor.processBookingConfirmed(
                BookingDto.builder()
                        .topic(record.topic())
                        .key(record.key())
                        .bookingId(event.getBookingId())
                        .eventId(event.getEventId())
                        .totalPrice(event.getTotalPrice())
                        .currency(event.getCurrency())
                        .eventTitle(event.getEventTitle())
                        .seatCategory(event.getSeatCategory())
                        .build());
    }

    @KafkaListener(
            topics = KafkaTopics.BOOKING_CANCELLED,
            containerFactory = BkgAnalyticsConstants.BkgBookingConstants.BOOKING_CANCELLED_FACTORY
    )
    public void onBookingCancelled(ConsumerRecord<String, BookingCancelledEvent> record) {
        BookingCancelledEvent event = record.value();

        log.info("[BOOKING_CANCELLED] bookingId='{}', eventId='{}', reason='{}' | partition={}, offset={}",
                event.getBookingId(), event.getEventId(), event.getReason(),
                record.partition(), record.offset());

        processor.processBookingCancelled(
                BookingDto.builder()
                        .topic(record.topic())
                        .key(record.key())
                        .bookingId(event.getBookingId())
                        .eventId(event.getEventId())
                        .build());
    }
}
