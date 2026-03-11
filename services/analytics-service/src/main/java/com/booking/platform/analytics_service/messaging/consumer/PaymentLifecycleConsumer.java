package com.booking.platform.analytics_service.messaging.consumer;

import com.booking.platform.analytics_service.constants.BkgAnalyticsConstants;
import com.booking.platform.analytics_service.dto.PaymentDto;
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
            containerFactory = BkgAnalyticsConstants.Payment.COMPLETED_FACTORY
    )
    public void onPaymentCompleted(ConsumerRecord<String, PaymentCompletedEvent> record) {
        PaymentCompletedEvent event = record.value();

        log.debug("[PAYMENT_COMPLETED] paymentId='{}', bookingId='{}', amount={} {} | partition={}, offset={}",
                event.getPaymentId(), event.getBookingId(),
                event.getAmount(), event.getCurrency(),
                record.partition(), record.offset());

        processor.processPaymentCompleted(
                PaymentDto.builder()
                        .topic(record.topic())
                        .key(record.key())
                        .paymentId(event.getPaymentId())
                        .bookingId(event.getBookingId())
                        .amount(event.getAmount())
                        .currency(event.getCurrency())
                        .build());
    }

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_FAILED,
            containerFactory = BkgAnalyticsConstants.Payment.FAILED_FACTORY
    )
    public void onPaymentFailed(ConsumerRecord<String, PaymentFailedEvent> record) {
        PaymentFailedEvent event = record.value();

        log.debug("[PAYMENT_FAILED] paymentId='{}', bookingId='{}', reason='{}' | partition={}, offset={}",
                event.getPaymentId(), event.getBookingId(), event.getReason(),
                record.partition(), record.offset());

        processor.processPaymentFailed(
                PaymentDto.builder()
                        .topic(record.topic())
                        .key(record.key())
                        .paymentId(event.getPaymentId())
                        .bookingId(event.getBookingId())
                        .reason(event.getReason())
                        .build());

    }

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_REFUND_COMPLETED,
            containerFactory = BkgAnalyticsConstants.Payment.REFUND_FACTORY
    )
    public void onRefundCompleted(ConsumerRecord<String, RefundCompletedEvent> record) {
        RefundCompletedEvent event = record.value();

        log.debug("[REFUND_COMPLETED] paymentId='{}', bookingId='{}', refundId='{}', amount={} {} | partition={}, offset={}",
                event.getPaymentId(), event.getBookingId(), event.getRefundId(),
                event.getAmount(), event.getCurrency(),
                record.partition(), record.offset());

        processor.processRefundCompleted(
                PaymentDto.builder()
                        .topic(record.topic())
                        .key(record.key())
                        .paymentId(event.getPaymentId())
                        .bookingId(event.getBookingId())
                        .amount(event.getAmount())
                        .currency(event.getCurrency())
                        .refundId(event.getRefundId())
                        .build());
    }

}
