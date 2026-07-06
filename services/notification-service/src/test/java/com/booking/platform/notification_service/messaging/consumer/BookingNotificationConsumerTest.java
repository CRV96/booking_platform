package com.booking.platform.notification_service.messaging.consumer;

import com.booking.platform.common.events.BookingCancelledEvent;
import com.booking.platform.common.events.BookingConfirmedEvent;
import com.booking.platform.common.events.BookingCreatedEvent;
import com.booking.platform.common.events.PaymentFailedEvent;
import com.booking.platform.notification_service.constants.EmailTemplatesConst;
import com.booking.platform.notification_service.email.EmailService;
import com.booking.platform.notification_service.grpc.client.UserServiceClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingNotificationConsumerTest {

    @Mock private EmailService emailService;
    @Mock private UserServiceClient userServiceClient;

    @InjectMocks private BookingNotificationConsumer consumer;

    // ── onBookingCreated — log-only, no email ─────────────────────────────────

    @Test
    void onBookingCreated_noEmailSent() {
        BookingCreatedEvent event = BookingCreatedEvent.newBuilder()
                .setBookingId("bk-1").setEventId("ev-1").setUserId("u-1")
                .setSeatCategory("VIP").setQuantity(2).setTotalPrice(199.99).setCurrency("USD")
                .build();
        ConsumerRecord<String, BookingCreatedEvent> record =
                new ConsumerRecord<>("events.booking.created", 0, 1L, "key", event);

        consumer.onBookingCreated(record);

        verifyNoInteractions(emailService);
        verifyNoInteractions(userServiceClient);
    }

    // ── onBookingConfirmed ────────────────────────────────────────────────────

    @Test
    void onBookingConfirmed_fetchesEmailFromUserService() {
        BookingConfirmedEvent event = BookingConfirmedEvent.newBuilder()
                .setBookingId("bk-1").setEventId("ev-1").setUserId("u-42")
                .addAllTicketIds(List.of("t1", "t2"))
                .setSeatCategory("GA").setQuantity(2).setTotalPrice(99.99).setCurrency("USD")
                .setTimestamp("2024-01-01T10:00:00Z")
                .build();
        ConsumerRecord<String, BookingConfirmedEvent> record =
                new ConsumerRecord<>("events.booking.confirmed", 0, 1L, "key", event);
        when(userServiceClient.getUserEmail("u-42")).thenReturn("alice@test.com");

        consumer.onBookingConfirmed(record);

        verify(userServiceClient).getUserEmail("u-42");
    }

    @Test
    void onBookingConfirmed_sendsEmailToRecipient() {
        BookingConfirmedEvent event = BookingConfirmedEvent.newBuilder()
                .setBookingId("bk-99").setEventId("ev-1").setUserId("u-1")
                .addAllTicketIds(List.of("t1"))
                .setSeatCategory("GA").setQuantity(1).setTotalPrice(50.0).setCurrency("EUR")
                .setTimestamp("2024-06-01T12:00:00Z")
                .build();
        ConsumerRecord<String, BookingConfirmedEvent> record =
                new ConsumerRecord<>("events.booking.confirmed", 0, 1L, "key", event);
        when(userServiceClient.getUserEmail("u-1")).thenReturn("bob@test.com");

        consumer.onBookingConfirmed(record);

        verify(emailService).sendHtml(
                eq("bob@test.com"),
                eq(EmailTemplatesConst.BookingConfirmation.SUBJECT),
                eq(EmailTemplatesConst.BookingConfirmation.TEMPLATE),
                any());
    }

    @Test
    void onBookingConfirmed_variablesContainEventFields() {
        BookingConfirmedEvent event = BookingConfirmedEvent.newBuilder()
                .setBookingId("bk-7").setEventId("ev-3").setUserId("u-5")
                .addAllTicketIds(List.of("t10", "t11"))
                .setSeatCategory("PREMIUM").setQuantity(2).setTotalPrice(149.50).setCurrency("GBP")
                .setTimestamp("2024-03-15T09:00:00Z")
                .build();
        ConsumerRecord<String, BookingConfirmedEvent> record =
                new ConsumerRecord<>("events.booking.confirmed", 0, 1L, "key", event);
        when(userServiceClient.getUserEmail("u-5")).thenReturn("carol@test.com");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        consumer.onBookingConfirmed(record);

        verify(emailService).sendHtml(anyString(), anyString(), anyString(), varsCaptor.capture());
        Map<String, Object> vars = varsCaptor.getValue();
        assertThat(vars.get(EmailTemplatesConst.BookingConfirmation.Vars.BOOKING_ID)).isEqualTo("bk-7");
        assertThat(vars.get(EmailTemplatesConst.BookingConfirmation.Vars.EVENT_ID)).isEqualTo("ev-3");
        assertThat(vars.get(EmailTemplatesConst.BookingConfirmation.Vars.SEAT_CATEGORY)).isEqualTo("PREMIUM");
        assertThat(vars.get(EmailTemplatesConst.BookingConfirmation.Vars.CURRENCY)).isEqualTo("GBP");
        assertThat(vars.get(EmailTemplatesConst.BookingConfirmation.Vars.TIMESTAMP)).isEqualTo("2024-03-15T09:00:00Z");
    }

    // ── onBookingCancelled ────────────────────────────────────────────────────

    @Test
    void onBookingCancelled_fetchesEmailFromUserService() {
        BookingCancelledEvent event = BookingCancelledEvent.newBuilder()
                .setBookingId("bk-1").setEventId("ev-1").setUserId("u-77")
                .setReason("User cancelled").setTimestamp("2024-01-01T00:00:00Z")
                .build();
        ConsumerRecord<String, BookingCancelledEvent> record =
                new ConsumerRecord<>("events.booking.cancelled", 0, 1L, "key", event);
        when(userServiceClient.getUserEmail("u-77")).thenReturn("dan@test.com");

        consumer.onBookingCancelled(record);

        verify(userServiceClient).getUserEmail("u-77");
    }

    @Test
    void onBookingCancelled_sendsEmailWithCorrectTemplate() {
        BookingCancelledEvent event = BookingCancelledEvent.newBuilder()
                .setBookingId("bk-2").setEventId("ev-2").setUserId("u-3")
                .setReason("Payment failed").setTimestamp("2024-02-01T00:00:00Z")
                .build();
        ConsumerRecord<String, BookingCancelledEvent> record =
                new ConsumerRecord<>("events.booking.cancelled", 0, 1L, "key", event);
        when(userServiceClient.getUserEmail("u-3")).thenReturn("eve@test.com");

        consumer.onBookingCancelled(record);

        verify(emailService).sendHtml(
                eq("eve@test.com"),
                eq(EmailTemplatesConst.BookingCancellation.SUBJECT),
                eq(EmailTemplatesConst.BookingCancellation.TEMPLATE),
                any());
    }

    @Test
    void onBookingCancelled_variablesContainEventFields() {
        BookingCancelledEvent event = BookingCancelledEvent.newBuilder()
                .setBookingId("bk-5").setEventId("ev-9").setUserId("u-1")
                .setReason("Venue closed").setTimestamp("2024-05-10T08:00:00Z")
                .build();
        ConsumerRecord<String, BookingCancelledEvent> record =
                new ConsumerRecord<>("events.booking.cancelled", 0, 1L, "key", event);
        when(userServiceClient.getUserEmail("u-1")).thenReturn("frank@test.com");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        consumer.onBookingCancelled(record);

        verify(emailService).sendHtml(anyString(), anyString(), anyString(), varsCaptor.capture());
        Map<String, Object> vars = varsCaptor.getValue();
        assertThat(vars.get(EmailTemplatesConst.BookingCancellation.Vars.BOOKING_ID)).isEqualTo("bk-5");
        assertThat(vars.get(EmailTemplatesConst.BookingCancellation.Vars.EVENT_ID)).isEqualTo("ev-9");
        assertThat(vars.get(EmailTemplatesConst.BookingCancellation.Vars.REASON)).isEqualTo("Venue closed");
        assertThat(vars.get(EmailTemplatesConst.BookingCancellation.Vars.TIMESTAMP)).isEqualTo("2024-05-10T08:00:00Z");
    }

    // ── onPaymentFailed — log-only, no email ──────────────────────────────────

    @Test
    void onPaymentFailed_noEmailSent() {
        PaymentFailedEvent event = PaymentFailedEvent.newBuilder()
                .setPaymentId("pay-1").setBookingId("bk-1")
                .setReason("Card declined").setTimestamp("2024-01-01T00:00:00Z")
                .build();
        ConsumerRecord<String, PaymentFailedEvent> record =
                new ConsumerRecord<>("events.payment.failed", 0, 1L, "key", event);

        consumer.onPaymentFailed(record);

        verifyNoInteractions(emailService);
        verifyNoInteractions(userServiceClient);
    }
}
