package com.booking.platform.common.grpc.context;

import io.grpc.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdContextTest {

    @AfterEach
    void tearDown() {
        MDC.remove(CorrelationIdContext.MDC_KEY);
    }

    @Test
    void get_returnsNullOutsideGrpcContext() {
        assertThat(CorrelationIdContext.get()).isNull();
    }

    @Test
    void get_returnsValueWhenSetInContext() {
        Context ctx = Context.current()
                .withValue(CorrelationIdContext.CORRELATION_ID, "trace-abc");
        Context prev = ctx.attach();
        try {
            assertThat(CorrelationIdContext.get()).isEqualTo("trace-abc");
        } finally {
            ctx.detach(prev);
        }
    }

    @Test
    void getOrGenerate_generatesUuidWhenContextIsEmpty() {
        String id = CorrelationIdContext.getOrGenerate();

        assertThat(id).isNotNull().isNotBlank();
        // basic UUID format: 8-4-4-4-12 hex characters
        assertThat(id).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void getOrGenerate_returnExistingValueWhenPresent() {
        Context ctx = Context.current()
                .withValue(CorrelationIdContext.CORRELATION_ID, "existing-id");
        Context prev = ctx.attach();
        try {
            assertThat(CorrelationIdContext.getOrGenerate()).isEqualTo("existing-id");
        } finally {
            ctx.detach(prev);
        }
    }

    @Test
    void getOrGenerate_generatesDifferentIdEachCall() {
        String id1 = CorrelationIdContext.getOrGenerate();
        String id2 = CorrelationIdContext.getOrGenerate();

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void setMdc_putsMdcKey() {
        CorrelationIdContext.setMdc("my-correlation-id");

        assertThat(MDC.get(CorrelationIdContext.MDC_KEY)).isEqualTo("my-correlation-id");
    }

    @Test
    void clearMdc_removesMdcKey() {
        MDC.put(CorrelationIdContext.MDC_KEY, "to-be-removed");

        CorrelationIdContext.clearMdc();

        assertThat(MDC.get(CorrelationIdContext.MDC_KEY)).isNull();
    }

    @Test
    void constants_haveExpectedValues() {
        assertThat(CorrelationIdContext.MDC_KEY).isEqualTo("correlationId");
        assertThat(CorrelationIdContext.HEADER_NAME).isEqualTo("x-correlation-id");
    }
}
