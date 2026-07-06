package com.booking.platform.common.events.interceptor;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Spring Kafka record interceptor that extracts the correlation ID from
 * incoming Kafka record headers and places it in SLF4J MDC before the
 * listener method executes.
 */
public class CorrelationIdKafkaConsumerInterceptor implements RecordInterceptor<Object, Object> {

    private static final String MDC_KEY = "correlationId";
    private static final String HEADER_NAME = "x-correlation-id";

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record,
                                                     Consumer<Object, Object> consumer) {
        Header header = record.headers().lastHeader(HEADER_NAME);
        String correlationId;
        if (header != null && header.value() != null) {
            correlationId = new String(header.value(), StandardCharsets.UTF_8);
        } else {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record,
                            Consumer<Object, Object> consumer) {
        MDC.remove(MDC_KEY);
    }
}
