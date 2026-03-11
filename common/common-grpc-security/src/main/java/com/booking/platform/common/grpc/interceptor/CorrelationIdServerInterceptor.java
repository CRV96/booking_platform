package com.booking.platform.common.grpc.interceptor;

import com.booking.platform.common.grpc.context.CorrelationIdContext;
import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.core.annotation.Order;

import java.util.UUID;

/**
 * gRPC server interceptor that extracts or generates a correlation ID for every
 * incoming call and places it in both the gRPC {@link Context} and SLF4J MDC.
 *
 * <p>Runs before JWT validation so the correlation ID is available in all log
 * lines, including authentication failures.
 */
@Slf4j
@GrpcGlobalServerInterceptor
@Order(InterceptorOrder.CORRELATION_ID)
public class CorrelationIdServerInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> CORRELATION_ID_KEY =
            Metadata.Key.of(CorrelationIdContext.HEADER_NAME, Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String correlationId = headers.get(CORRELATION_ID_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        CorrelationIdContext.setMdc(correlationId);

        Context context = Context.current()
                .withValue(CorrelationIdContext.CORRELATION_ID, correlationId);

        return new ContextualForwardingListener<>(
                Contexts.interceptCall(context, call, headers, next));
    }

    /**
     * Wraps the delegate listener to clean up MDC on completion or cancellation.
     */
    private static class ContextualForwardingListener<ReqT>
            extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {

        ContextualForwardingListener(ServerCall.Listener<ReqT> delegate) {
            super(delegate);
        }

        @Override
        public void onComplete() {
            try {
                super.onComplete();
            } finally {
                CorrelationIdContext.clearMdc();
            }
        }

        @Override
        public void onCancel() {
            try {
                super.onCancel();
            } finally {
                CorrelationIdContext.clearMdc();
            }
        }
    }
}
