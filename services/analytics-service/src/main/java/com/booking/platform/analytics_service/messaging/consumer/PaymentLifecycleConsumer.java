package com.booking.platform.analytics_service.messaging.consumer;

import com.booking.platform.analytics_service.service.PaymentAnalyticsProcessor;
import com.booking.platform.common.events.*;
import com.booking.platform.common.events.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for payment lifecycle topics.
 *
 * <p>Handles: PaymentCompleted, PaymentFailed, RefundCompleted.
 * Each listener extracts fields from the proto message and delegates
 * to {@link PaymentAnalyticsProcessor}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentLifecycleConsumer {

    private final PaymentAnalyticsProcessor processor;

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_COMPLETED,
            containerFactory = "paymentCompletedListenerFactory"
    )
    public void onPaymentCompleted(ConsumerRecord<String, PaymentCompletedEvent> record) {
        PaymentCompletedEvent event = record.value();
        log.info("[PAYMENT_COMPLETED] paymentId='{}', bookingId='{}', amount={} {} | partition={}, offset={}",
                event.getPaymentId(), event.getBookingId(),
                event.getAmount(), event.getCurrency(),
                record.partition(), record.offset());

        processor.processPaymentCompleted(
                record.topic(), record.key(),
                event.getPaymentId(), event.getBookingId(),
                event.getAmount(), event.getCurrency());
    }

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_FAILED,
            containerFactory = "paymentFailedListenerFactory"
    )
    public void onPaymentFailed(ConsumerRecord<String, PaymentFailedEvent> record) {
        PaymentFailedEvent event = record.value();
        log.warn("[PAYMENT_FAILED] paymentId='{}', bookingId='{}', reason='{}' | partition={}, offset={}",
                event.getPaymentId(), event.getBookingId(), event.getReason(),
                record.partition(), record.offset());

        processor.processPaymentFailed(
                record.topic(), record.key(),
                event.getPaymentId(), event.getBookingId(), event.getReason());
    }

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_REFUND_COMPLETED,
            containerFactory = "refundCompletedListenerFactory"
    )
    public void onRefundCompleted(ConsumerRecord<String, RefundCompletedEvent> record) {
        RefundCompletedEvent event = record.value();
        log.info("[REFUND_COMPLETED] paymentId='{}', bookingId='{}', refundId='{}', amount={} {} | partition={}, offset={}",
                event.getPaymentId(), event.getBookingId(), event.getRefundId(),
                event.getAmount(), event.getCurrency(),
                record.partition(), record.offset());

        processor.processRefundCompleted(
                record.topic(), record.key(),
                event.getPaymentId(), event.getBookingId(),
                event.getRefundId(), event.getAmount(), event.getCurrency());
    }
}
