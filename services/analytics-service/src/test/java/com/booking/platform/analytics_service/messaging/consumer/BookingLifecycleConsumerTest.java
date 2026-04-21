package com.booking.platform.analytics_service.messaging.consumer;

import com.booking.platform.analytics_service.dto.BookingDto;
import com.booking.platform.analytics_service.service.BookingAnalyticsProcessor;
import com.booking.platform.common.events.BookingCancelledEvent;
import com.booking.platform.common.events.BookingConfirmedEvent;
import com.booking.platform.common.events.BookingCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingLifecycleConsumerTest {

    @Mock private BookingAnalyticsProcessor processor;

    @InjectMocks private BookingLifecycleConsumer consumer;

    private <V> ConsumerRecord<String, V> record(String topic, String key, V value) {
        return new ConsumerRecord<>(topic, 0, 0L, key, value);
    }

    // ── onBookingCreated ──────────────────────────────────────────────────────

    @Test
    void onBookingCreated_buildsCorrectDtoAndDelegates() {
        BookingCreatedEvent event = BookingCreatedEvent.newBuilder()
                .setBookingId("bk-1").setEventId("ev-1")
                .setTotalPrice(100.0).setCurrency("USD")
                .build();

        consumer.onBookingCreated(record("booking.created", "bk-1", event));

        ArgumentCaptor<BookingDto> captor = ArgumentCaptor.forClass(BookingDto.class);
        verify(processor).processBookingCreated(captor.capture());

        BookingDto dto = captor.getValue();
        assertThat(dto.bookingId()).isEqualTo("bk-1");
        assertThat(dto.eventId()).isEqualTo("ev-1");
        assertThat(dto.totalPrice()).isEqualTo(100.0);
        assertThat(dto.currency()).isEqualTo("USD");
        assertThat(dto.topic()).isEqualTo("booking.created");
        assertThat(dto.key()).isEqualTo("bk-1");
    }

    // ── onBookingConfirmed ────────────────────────────────────────────────────

    @Test
    void onBookingConfirmed_buildsCorrectDtoWithEventTitleAndCategory() {
        BookingConfirmedEvent event = BookingConfirmedEvent.newBuilder()
                .setBookingId("bk-2").setEventId("ev-2")
                .setTotalPrice(50.0).setCurrency("EUR")
                .setEventTitle("Rock Fest").setSeatCategory("VIP")
                .build();

        consumer.onBookingConfirmed(record("booking.confirmed", "bk-2", event));

        ArgumentCaptor<BookingDto> captor = ArgumentCaptor.forClass(BookingDto.class);
        verify(processor).processBookingConfirmed(captor.capture());

        BookingDto dto = captor.getValue();
        assertThat(dto.bookingId()).isEqualTo("bk-2");
        assertThat(dto.eventTitle()).isEqualTo("Rock Fest");
        assertThat(dto.seatCategory()).isEqualTo("VIP");
        assertThat(dto.totalPrice()).isEqualTo(50.0);
    }

    // ── onBookingCancelled ────────────────────────────────────────────────────

    @Test
    void onBookingCancelled_buildsCorrectDtoWithReason() {
        BookingCancelledEvent event = BookingCancelledEvent.newBuilder()
                .setBookingId("bk-3").setEventId("ev-3")
                .setReason("changed mind")
                .build();

        consumer.onBookingCancelled(record("booking.cancelled", "bk-3", event));

        ArgumentCaptor<BookingDto> captor = ArgumentCaptor.forClass(BookingDto.class);
        verify(processor).processBookingCancelled(captor.capture());

        BookingDto dto = captor.getValue();
        assertThat(dto.bookingId()).isEqualTo("bk-3");
        assertThat(dto.eventId()).isEqualTo("ev-3");
        assertThat(dto.reason()).isEqualTo("changed mind");
    }
}
