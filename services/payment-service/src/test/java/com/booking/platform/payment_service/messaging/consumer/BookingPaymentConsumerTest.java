package com.booking.platform.payment_service.messaging.consumer;

import com.booking.platform.common.events.BookingCreatedEvent;
import com.booking.platform.payment_service.service.PaymentService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingPaymentConsumerTest {

    @Mock private PaymentService paymentService;
    @InjectMocks private BookingPaymentConsumer consumer;

    private ConsumerRecord<String, BookingCreatedEvent> record(String bookingId, String userId,
                                                               double totalPrice, String currency) {
        BookingCreatedEvent event = BookingCreatedEvent.newBuilder()
                .setBookingId(bookingId)
                .setEventId("event-1")
                .setUserId(userId)
                .setTotalPrice(totalPrice)
                .setCurrency(currency)
                .build();
        return new ConsumerRecord<>("events.booking.created", 0, 100L, "key", event);
    }

    @Test
    void onBookingCreated_delegatesToPaymentService() {
        consumer.onBookingCreated(record("booking-1", "user-1", 99.99, "USD"));

        verify(paymentService).processPayment(
                "booking-1", "user-1", BigDecimal.valueOf(99.99), "USD");
    }

    @Test
    void onBookingCreated_mapsAllEventFields() {
        consumer.onBookingCreated(record("booking-42", "user-99", 149.50, "EUR"));

        ArgumentCaptor<String> bookingCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<String> currencyCaptor = ArgumentCaptor.forClass(String.class);

        verify(paymentService).processPayment(
                bookingCaptor.capture(), userCaptor.capture(),
                amountCaptor.capture(), currencyCaptor.capture());

        assertThat(bookingCaptor.getValue()).isEqualTo("booking-42");
        assertThat(userCaptor.getValue()).isEqualTo("user-99");
        assertThat(amountCaptor.getValue()).isEqualByComparingTo("149.50");
        assertThat(currencyCaptor.getValue()).isEqualTo("EUR");
    }

    @Test
    void onBookingCreated_totalPriceConvertedToBigDecimal() {
        consumer.onBookingCreated(record("b", "u", 1.0, "USD"));

        ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(paymentService).processPayment(any(), any(), amountCaptor.capture(), any());

        assertThat(amountCaptor.getValue()).isEqualByComparingTo("1.0");
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
