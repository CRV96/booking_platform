package com.booking.platform.common.events.interceptor;

import com.google.protobuf.MessageLite;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdKafkaProducerInterceptorTest {

    private static final String MDC_KEY = "correlationId";
    private static final String HEADER_NAME = "x-correlation-id";

    private CorrelationIdKafkaProducerInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new CorrelationIdKafkaProducerInterceptor();
    }

    @AfterEach
    void tearDown() {
        MDC.remove(MDC_KEY);
    }

    @Test
    void onSend_addsCorrelationIdHeaderWhenMdcIsSet() {
        MDC.put(MDC_KEY, "trace-123");
        ProducerRecord<String, MessageLite> record = new ProducerRecord<>("topic", null);

        ProducerRecord<String, MessageLite> result = interceptor.onSend(record);

        assertThat(result.headers().lastHeader(HEADER_NAME)).isNotNull();
        String headerValue = new String(result.headers().lastHeader(HEADER_NAME).value(), StandardCharsets.UTF_8);
        assertThat(headerValue).isEqualTo("trace-123");
    }

    @Test
    void onSend_doesNotAddHeaderWhenMdcIsAbsent() {
        ProducerRecord<String, MessageLite> record = new ProducerRecord<>("topic", null);

        ProducerRecord<String, MessageLite> result = interceptor.onSend(record);

        assertThat(result.headers().lastHeader(HEADER_NAME)).isNull();
    }

    @Test
    void onSend_returnsTheSameRecord() {
        ProducerRecord<String, MessageLite> record = new ProducerRecord<>("topic", null);

        ProducerRecord<String, MessageLite> result = interceptor.onSend(record);

        assertThat(result).isSameAs(record);
    }

    @Test
    void onSend_encodesCorrelationIdAsUtf8() {
        String id = "cörrelation-ïd";
        MDC.put(MDC_KEY, id);
        ProducerRecord<String, MessageLite> record = new ProducerRecord<>("topic", null);

        interceptor.onSend(record);

        byte[] headerBytes = record.headers().lastHeader(HEADER_NAME).value();
        assertThat(new String(headerBytes, StandardCharsets.UTF_8)).isEqualTo(id);
    }
}
