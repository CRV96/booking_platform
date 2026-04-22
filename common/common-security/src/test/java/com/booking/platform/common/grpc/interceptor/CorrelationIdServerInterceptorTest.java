package com.booking.platform.common.grpc.interceptor;

import com.booking.platform.common.grpc.context.CorrelationIdContext;
import io.grpc.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrelationIdServerInterceptorTest {

    @Mock private ServerCall<Object, Object> call;
    @Mock private ServerCallHandler<Object, Object> handler;
    @Mock private ServerCall.Listener<Object> listener;

    private final CorrelationIdServerInterceptor interceptor = new CorrelationIdServerInterceptor();

    @BeforeEach
    void setUp() {
        when(handler.startCall(any(), any())).thenReturn(listener);
    }

    @AfterEach
    void cleanup() {
        MDC.remove(CorrelationIdContext.MDC_KEY);
    }

    private static final Metadata.Key<String> CORRELATION_HEADER =
            Metadata.Key.of(CorrelationIdContext.HEADER_NAME, Metadata.ASCII_STRING_MARSHALLER);

    private Metadata headersWithCorrelationId(String id) {
        Metadata headers = new Metadata();
        headers.put(CORRELATION_HEADER, id);
        return headers;
    }

    // ── header present ────────────────────────────────────────────────────────

    @Test
    void headerPresent_proceedsToHandler() {
        interceptor.interceptCall(call, headersWithCorrelationId("existing-id"), handler);

        verify(handler).startCall(any(), any());
    }

    @Test
    void headerPresent_setsMdcDuringCall() {
        interceptor.interceptCall(call, headersWithCorrelationId("corr-xyz"), handler);

        // MDC is set during the interceptor; after startCall the MDC should still be active
        // (cleared only when listener.onComplete/onCancel is invoked)
        assertThat(MDC.get(CorrelationIdContext.MDC_KEY)).isEqualTo("corr-xyz");
    }

    // ── header missing — UUID generated ──────────────────────────────────────

    @Test
    void headerMissing_generatesCorrelationId_andProceeds() {
        interceptor.interceptCall(call, new Metadata(), handler);

        verify(handler).startCall(any(), any());
        String generated = MDC.get(CorrelationIdContext.MDC_KEY);
        assertThat(generated).isNotNull().isNotBlank();
    }

    @Test
    void headerMissing_generatedIdIsValidUUID() {
        interceptor.interceptCall(call, new Metadata(), handler);

        String generated = MDC.get(CorrelationIdContext.MDC_KEY);
        // UUIDs have 36 characters with 4 hyphens
        assertThat(generated).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    // ── blank header — UUID generated ────────────────────────────────────────

    @Test
    void blankHeader_generatesNewCorrelationId() {
        Metadata headers = new Metadata();
        headers.put(CORRELATION_HEADER, "  ");

        interceptor.interceptCall(call, headers, handler);

        verify(handler).startCall(any(), any());
        String generated = MDC.get(CorrelationIdContext.MDC_KEY);
        assertThat(generated).isNotBlank();
        assertThat(generated).isNotEqualTo("  ");
    }

    // ── MDC lifecycle ─────────────────────────────────────────────────────────

    @Test
    void onComplete_clearsMdc() {
        ServerCall.Listener<Object> wrappedListener =
                interceptor.interceptCall(call, headersWithCorrelationId("corr-abc"), handler);

        assertThat(MDC.get(CorrelationIdContext.MDC_KEY)).isEqualTo("corr-abc");

        wrappedListener.onComplete();

        assertThat(MDC.get(CorrelationIdContext.MDC_KEY)).isNull();
    }

    @Test
    void onCancel_clearsMdc() {
        ServerCall.Listener<Object> wrappedListener =
                interceptor.interceptCall(call, headersWithCorrelationId("corr-def"), handler);

        assertThat(MDC.get(CorrelationIdContext.MDC_KEY)).isEqualTo("corr-def");

        wrappedListener.onCancel();

        assertThat(MDC.get(CorrelationIdContext.MDC_KEY)).isNull();
    }
}
