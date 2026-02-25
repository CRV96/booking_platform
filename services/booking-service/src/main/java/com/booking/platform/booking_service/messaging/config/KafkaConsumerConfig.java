package com.booking.platform.booking_service.messaging.config;

import com.booking.platform.common.events.PaymentCompletedEvent;
import com.booking.platform.common.events.PaymentFailedEvent;
import com.booking.platform.common.events.config.BaseKafkaConsumerConfig;
import com.booking.platform.common.events.serialization.ProtobufDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;

import java.util.Map;

/**
 * Kafka consumer configuration for booking-service.
 *
 * <p>Booking-service consumes from two payment topics:
 * <ul>
 *   <li>{@code events.payment.completed} — triggers booking confirmation (P3-06)</li>
 *   <li>{@code events.payment.failed} — triggers booking cancellation + seat release (P3-07)</li>
 * </ul>
 *
 * <p>Base infrastructure (error handler, DLT, default factory) is inherited
 * from {@link BaseKafkaConsumerConfig}; only the typed event factory is defined here.
 */
@Configuration
public class KafkaConsumerConfig extends BaseKafkaConsumerConfig {

    // ── PaymentCompletedEvent ─────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, PaymentCompletedEvent> paymentCompletedConsumerFactory() {
        Map<String, Object> config = baseConfig();
        config.put(ProtobufDeserializer.PARSER_CONFIG_KEY, PaymentCompletedEvent.parser());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentCompletedEvent> paymentCompletedListenerFactory(
            CommonErrorHandler errorHandler) {
        return buildFactory(paymentCompletedConsumerFactory(), errorHandler);
    }

    // ── PaymentFailedEvent (P3-07 compensation) ──────────────────────────────

    @Bean
    public ConsumerFactory<String, PaymentFailedEvent> paymentFailedConsumerFactory() {
        Map<String, Object> config = baseConfig();
        config.put(ProtobufDeserializer.PARSER_CONFIG_KEY, PaymentFailedEvent.parser());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentFailedEvent> paymentFailedListenerFactory(
            CommonErrorHandler errorHandler) {
        return buildFactory(paymentFailedConsumerFactory(), errorHandler);
    }
}
