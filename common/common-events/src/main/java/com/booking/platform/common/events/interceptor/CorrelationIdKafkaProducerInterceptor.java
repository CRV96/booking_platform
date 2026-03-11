package com.booking.platform.common.events.interceptor;

import com.google.protobuf.MessageLite;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Kafka producer interceptor that copies the correlation ID from MDC into
 * the outgoing record headers.
 *
 * <p>{@code onSend} runs on the calling thread, so the MDC value set by
 * the gRPC server interceptor or HTTP filter is available.
 */
public class CorrelationIdKafkaProducerInterceptor implements ProducerInterceptor<String, MessageLite> {

    private static final String MDC_KEY = "correlationId";
    private static final String HEADER_NAME = "x-correlation-id";

    @Override
    public ProducerRecord<String, MessageLite> onSend(ProducerRecord<String, MessageLite> record) {
        String correlationId = MDC.get(MDC_KEY);
        if (correlationId != null) {
            record.headers().add(HEADER_NAME, correlationId.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }

    @Override
    public void configure(Map<String, ?> configs) {
        // no-op
    }
}
