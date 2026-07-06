package com.booking.platform.payment_service.messaging.consumer;

import com.booking.platform.common.events.BookingCancelledEvent;
import com.booking.platform.payment_service.service.PaymentService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingCancelledConsumerTest {

    @Mock private PaymentService paymentService;
    @InjectMocks private BookingCancelledConsumer consumer;

    private ConsumerRecord<String, BookingCancelledEvent> record(String bookingId, String reason) {
        BookingCancelledEvent event = BookingCancelledEvent.newBuilder()
                .setBookingId(bookingId)
                .setReason(reason)
                .build();
        return new ConsumerRecord<>("events.booking.cancelled", 0, 50L, "key", event);
    }

    @Test
    void onBookingCancelled_delegatesToProcessRefund() {
        consumer.onBookingCancelled(record("booking-1", "User cancelled"));

        verify(paymentService).processRefund("booking-1");
    }

    @Test
    void onBookingCancelled_extractsBookingIdFromEvent() {
        consumer.onBookingCancelled(record("booking-99", "Event cancelled"));

        verify(paymentService).processRefund("booking-99");
    }
}
