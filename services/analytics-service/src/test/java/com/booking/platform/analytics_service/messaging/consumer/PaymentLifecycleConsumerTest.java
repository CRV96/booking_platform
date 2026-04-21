package com.booking.platform.analytics_service.messaging.consumer;

import com.booking.platform.analytics_service.dto.PaymentDto;
import com.booking.platform.analytics_service.service.PaymentAnalyticsProcessor;
import com.booking.platform.common.events.PaymentCompletedEvent;
import com.booking.platform.common.events.PaymentFailedEvent;
import com.booking.platform.common.events.RefundCompletedEvent;
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
class PaymentLifecycleConsumerTest {

    @Mock private PaymentAnalyticsProcessor processor;

    @InjectMocks private PaymentLifecycleConsumer consumer;

    private <V> ConsumerRecord<String, V> record(String topic, String key, V value) {
        return new ConsumerRecord<>(topic, 0, 0L, key, value);
    }

    // ── onPaymentCompleted ────────────────────────────────────────────────────

    @Test
    void onPaymentCompleted_buildsCorrectDto() {
        PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
                .setPaymentId("pay-1").setBookingId("bk-1")
                .setAmount(150.0).setCurrency("USD")
                .build();

        consumer.onPaymentCompleted(record("payment.completed", "pay-1", event));

        ArgumentCaptor<PaymentDto> captor = ArgumentCaptor.forClass(PaymentDto.class);
        verify(processor).processPaymentCompleted(captor.capture());

        PaymentDto dto = captor.getValue();
        assertThat(dto.paymentId()).isEqualTo("pay-1");
        assertThat(dto.bookingId()).isEqualTo("bk-1");
        assertThat(dto.amount()).isEqualTo(150.0);
        assertThat(dto.currency()).isEqualTo("USD");
        assertThat(dto.topic()).isEqualTo("payment.completed");
        assertThat(dto.key()).isEqualTo("pay-1");
    }

    // ── onPaymentFailed ───────────────────────────────────────────────────────

    @Test
    void onPaymentFailed_buildsCorrectDtoWithReason() {
        PaymentFailedEvent event = PaymentFailedEvent.newBuilder()
                .setPaymentId("pay-2").setBookingId("bk-2")
                .setReason("card declined")
                .build();

        consumer.onPaymentFailed(record("payment.failed", "pay-2", event));

        ArgumentCaptor<PaymentDto> captor = ArgumentCaptor.forClass(PaymentDto.class);
        verify(processor).processPaymentFailed(captor.capture());

        PaymentDto dto = captor.getValue();
        assertThat(dto.paymentId()).isEqualTo("pay-2");
        assertThat(dto.reason()).isEqualTo("card declined");
        assertThat(dto.refundId()).isNull();
    }

    // ── onRefundCompleted ─────────────────────────────────────────────────────

    @Test
    void onRefundCompleted_buildsCorrectDtoWithRefundId() {
        RefundCompletedEvent event = RefundCompletedEvent.newBuilder()
                .setPaymentId("pay-3").setBookingId("bk-3")
                .setRefundId("ref-1").setAmount(75.0).setCurrency("EUR")
                .build();

        consumer.onRefundCompleted(record("payment.refund.completed", "pay-3", event));

        ArgumentCaptor<PaymentDto> captor = ArgumentCaptor.forClass(PaymentDto.class);
        verify(processor).processRefundCompleted(captor.capture());

        PaymentDto dto = captor.getValue();
        assertThat(dto.paymentId()).isEqualTo("pay-3");
        assertThat(dto.refundId()).isEqualTo("ref-1");
        assertThat(dto.amount()).isEqualTo(75.0);
        assertThat(dto.currency()).isEqualTo("EUR");
    }
}
