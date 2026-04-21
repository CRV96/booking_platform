package com.booking.platform.booking_service.messaging.publisher;

import com.booking.platform.booking_service.entity.BookingEntity;
import com.booking.platform.booking_service.entity.enums.BookingStatus;
import com.booking.platform.booking_service.messaging.publisher.impl.KafkaBookingEventPublisher;
import com.booking.platform.common.events.BookingCancelledEvent;
import com.booking.platform.common.events.BookingConfirmedEvent;
import com.booking.platform.common.events.BookingCreatedEvent;
import com.booking.platform.common.events.KafkaTopics;
import com.google.protobuf.MessageLite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaBookingEventPublisherTest {

    @Mock private KafkaTemplate<String, MessageLite> kafkaTemplate;

    @InjectMocks private KafkaBookingEventPublisher publisher;

    private static final UUID BOOKING_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @BeforeEach
    void setUp() {
        when(kafkaTemplate.send(anyString(), anyString(), any(MessageLite.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    }

    private BookingEntity bookingEntity() {
        return BookingEntity.builder()
                .id(BOOKING_ID)
                .userId("u-1")
                .eventId("ev-1")
                .eventTitle("Rock Fest")
                .status(BookingStatus.PENDING)
                .seatCategory("VIP")
                .quantity(3)
                .unitPrice(new BigDecimal("50.00"))
                .totalPrice(new BigDecimal("150.00"))
                .currency("EUR")
                .idempotencyKey("idem-key")
                .holdExpiresAt(Instant.now())
                .build();
    }

    // ── publishBookingCreated ─────────────────────────────────────────────────

    @Test
    void publishBookingCreated_sendsToCorrectTopic() {
        publisher.publishBookingCreated(bookingEntity());

        verify(kafkaTemplate).send(eq(KafkaTopics.BOOKING_CREATED), anyString(), any());
    }

    @Test
    void publishBookingCreated_usesBookingIdAsKey() {
        publisher.publishBookingCreated(bookingEntity());

        verify(kafkaTemplate).send(anyString(), eq(BOOKING_ID.toString()), any());
    }

    @Test
    void publishBookingCreated_messageContainsCorrectFields() {
        ArgumentCaptor<MessageLite> captor = ArgumentCaptor.forClass(MessageLite.class);
        publisher.publishBookingCreated(bookingEntity());

        verify(kafkaTemplate).send(anyString(), anyString(), captor.capture());
        BookingCreatedEvent msg = (BookingCreatedEvent) captor.getValue();

        assertThat(msg.getBookingId()).isEqualTo(BOOKING_ID.toString());
        assertThat(msg.getEventId()).isEqualTo("ev-1");
        assertThat(msg.getUserId()).isEqualTo("u-1");
        assertThat(msg.getSeatCategory()).isEqualTo("VIP");
        assertThat(msg.getQuantity()).isEqualTo(3);
        assertThat(msg.getTotalPrice()).isEqualTo(150.0);
        assertThat(msg.getCurrency()).isEqualTo("EUR");
    }

    // ── publishBookingConfirmed ───────────────────────────────────────────────

    @Test
    void publishBookingConfirmed_sendsToCorrectTopic() {
        publisher.publishBookingConfirmed(bookingEntity());

        verify(kafkaTemplate).send(eq(KafkaTopics.BOOKING_CONFIRMED), anyString(), any());
    }

    @Test
    void publishBookingConfirmed_messageContainsEventTitle() {
        ArgumentCaptor<MessageLite> captor = ArgumentCaptor.forClass(MessageLite.class);
        publisher.publishBookingConfirmed(bookingEntity());

        verify(kafkaTemplate).send(anyString(), anyString(), captor.capture());
        BookingConfirmedEvent msg = (BookingConfirmedEvent) captor.getValue();

        assertThat(msg.getEventTitle()).isEqualTo("Rock Fest");
        assertThat(msg.getBookingId()).isEqualTo(BOOKING_ID.toString());
    }

    // ── publishBookingCancelled ───────────────────────────────────────────────

    @Test
    void publishBookingCancelled_sendsToCorrectTopic() {
        publisher.publishBookingCancelled(bookingEntity());

        verify(kafkaTemplate).send(eq(KafkaTopics.BOOKING_CANCELLED), anyString(), any());
    }

    @Test
    void publishBookingCancelled_nullCancellationReason_sendsEmptyString() {
        ArgumentCaptor<MessageLite> captor = ArgumentCaptor.forClass(MessageLite.class);
        publisher.publishBookingCancelled(bookingEntity()); // cancellationReason = null

        verify(kafkaTemplate).send(anyString(), anyString(), captor.capture());
        BookingCancelledEvent msg = (BookingCancelledEvent) captor.getValue();

        assertThat(msg.getReason()).isEmpty();
    }

    @Test
    void publishBookingCancelled_withReason_sendsReason() {
        BookingEntity entity = bookingEntity();
        entity.setCancellationReason("changed mind");
        ArgumentCaptor<MessageLite> captor = ArgumentCaptor.forClass(MessageLite.class);

        publisher.publishBookingCancelled(entity);

        verify(kafkaTemplate).send(anyString(), anyString(), captor.capture());
        BookingCancelledEvent msg = (BookingCancelledEvent) captor.getValue();
        assertThat(msg.getReason()).isEqualTo("changed mind");
    }
}
