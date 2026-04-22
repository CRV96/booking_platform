package com.booking.platform.common.grpc.interceptor;

import com.booking.platform.common.grpc.context.CorrelationIdContext;
import io.grpc.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrelationIdClientInterceptorTest {

    @Mock private Channel channel;
    @Mock private ClientCall<Object, Object> clientCall;
    @Mock private ClientCall.Listener<Object> listener;

    private final CorrelationIdClientInterceptor interceptor = new CorrelationIdClientInterceptor();

    private Context.CancellableContext grpcCtx;
    private Context previousContext;

    @AfterEach
    void cleanup() {
        MDC.remove(CorrelationIdContext.MDC_KEY);
        if (grpcCtx != null) {
            grpcCtx.detach(previousContext);
            grpcCtx.cancel(null);
            grpcCtx = null;
        }
    }

    private static final Metadata.Key<String> CORRELATION_HEADER =
            Metadata.Key.of(CorrelationIdContext.HEADER_NAME, Metadata.ASCII_STRING_MARSHALLER);

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ClientCall<Object, Object> intercept() {
        MethodDescriptor<Object, Object> method = mock(MethodDescriptor.class);
        when(channel.newCall(any(MethodDescriptor.class), any(CallOptions.class)))
                .thenReturn((ClientCall) clientCall);
        return interceptor.interceptCall(method, CallOptions.DEFAULT, channel);
    }

    private void attachCorrelationId(String id) {
        grpcCtx = Context.current()
                .withValue(CorrelationIdContext.CORRELATION_ID, id)
                .withCancellation();
        previousContext = grpcCtx.attach();
    }

    // ── correlation ID from gRPC context ──────────────────────────────────────

    @Test
    void correlationIdInGrpcContext_addedToHeader() {
        attachCorrelationId("corr-123");

        ArgumentCaptor<Metadata> captor = ArgumentCaptor.forClass(Metadata.class);
        intercept().start(listener, new Metadata());

        verify(clientCall).start(any(), captor.capture());
        assertThat(captor.getValue().get(CORRELATION_HEADER)).isEqualTo("corr-123");
    }

    // ── correlation ID from MDC ───────────────────────────────────────────────

    @Test
    void correlationIdInMdc_addedToHeader() {
        MDC.put(CorrelationIdContext.MDC_KEY, "mdc-456");

        ArgumentCaptor<Metadata> captor = ArgumentCaptor.forClass(Metadata.class);
        intercept().start(listener, new Metadata());

        verify(clientCall).start(any(), captor.capture());
        assertThat(captor.getValue().get(CORRELATION_HEADER)).isEqualTo("mdc-456");
    }

    // ── no correlation ID ─────────────────────────────────────────────────────

    @Test
    void noCorrelationId_headerNotAdded() {
        ArgumentCaptor<Metadata> captor = ArgumentCaptor.forClass(Metadata.class);
        intercept().start(listener, new Metadata());

        verify(clientCall).start(any(), captor.capture());
        assertThat(captor.getValue().get(CORRELATION_HEADER)).isNull();
    }

    // ── gRPC context takes precedence over MDC ────────────────────────────────

    @Test
    void grpcContextTakesPrecedenceOverMdc() {
        attachCorrelationId("ctx-id");
        MDC.put(CorrelationIdContext.MDC_KEY, "mdc-id");

        ArgumentCaptor<Metadata> captor = ArgumentCaptor.forClass(Metadata.class);
        intercept().start(listener, new Metadata());

        verify(clientCall).start(any(), captor.capture());
        assertThat(captor.getValue().get(CORRELATION_HEADER)).isEqualTo("ctx-id");
    }
}
