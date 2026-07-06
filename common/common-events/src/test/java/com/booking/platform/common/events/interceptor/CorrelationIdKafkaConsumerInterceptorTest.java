package com.booking.platform.common.events.interceptor;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdKafkaConsumerInterceptorTest {

    private static final String MDC_KEY = "correlationId";
    private static final String HEADER_NAME = "x-correlation-id";

    private CorrelationIdKafkaConsumerInterceptor interceptor;

    // Consumer is unused by the interceptor — passing null avoids Mockito/Java-25 proxy issues
    private final Consumer<Object, Object> unusedConsumer = null;

    @BeforeEach
    void setUp() {
        interceptor = new CorrelationIdKafkaConsumerInterceptor();
    }

    @AfterEach
    void tearDown() {
        MDC.remove(MDC_KEY);
    }

    @Test
    void intercept_extractsCorrelationIdFromHeader() {
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("topic", 0, 0L, null, null);
        record.headers().add(new RecordHeader(HEADER_NAME, "trace-abc".getBytes(StandardCharsets.UTF_8)));

        interceptor.intercept(record, unusedConsumer);

        assertThat(MDC.get(MDC_KEY)).isEqualTo("trace-abc");
    }

    @Test
    void intercept_generatesUuidWhenHeaderIsAbsent() {
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("topic", 0, 0L, null, null);

        interceptor.intercept(record, unusedConsumer);

        String id = MDC.get(MDC_KEY);
        assertThat(id).isNotNull().isNotBlank();
        assertThat(id).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void intercept_generatesUuidWhenHeaderValueIsNull() {
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("topic", 0, 0L, null, null);
        record.headers().add(new RecordHeader(HEADER_NAME, null));

        interceptor.intercept(record, unusedConsumer);

        String id = MDC.get(MDC_KEY);
        assertThat(id).isNotNull().isNotBlank();
    }

    @Test
    void intercept_returnsTheSameRecord() {
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("topic", 0, 0L, null, null);

        ConsumerRecord<Object, Object> result = interceptor.intercept(record, unusedConsumer);

        assertThat(result).isSameAs(record);
    }

    @Test
    void afterRecord_removesMdcKey() {
        MDC.put(MDC_KEY, "trace-to-clear");
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("topic", 0, 0L, null, null);

        interceptor.afterRecord(record, unusedConsumer);

        assertThat(MDC.get(MDC_KEY)).isNull();
    }

    @Test
    void intercept_thenAfterRecord_leavesNoMdcTrace() {
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("topic", 0, 0L, null, null);
        record.headers().add(new RecordHeader(HEADER_NAME, "trace-xyz".getBytes(StandardCharsets.UTF_8)));

        interceptor.intercept(record, unusedConsumer);
        assertThat(MDC.get(MDC_KEY)).isEqualTo("trace-xyz");

        interceptor.afterRecord(record, unusedConsumer);
        assertThat(MDC.get(MDC_KEY)).isNull();
    }
}
