package com.booking.platform.common.grpc.interceptor;

import com.booking.platform.common.grpc.context.CorrelationIdContext;
import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.slf4j.MDC;

/**
 * gRPC client interceptor that propagates the correlation ID on outgoing calls.
 *
 * <p>Reads the correlation ID from the gRPC {@link Context} first, falling back
 * to the SLF4J MDC (useful when the call originates from an HTTP thread, e.g.
 * the GraphQL gateway).
 */
@Slf4j
@GrpcGlobalClientInterceptor
public class CorrelationIdClientInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> CORRELATION_ID_KEY =
            Metadata.Key.of(CorrelationIdContext.HEADER_NAME, Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String correlationId = CorrelationIdContext.CORRELATION_ID.get();
                if (correlationId == null) {
                    correlationId = MDC.get(CorrelationIdContext.MDC_KEY);
                }
                if (correlationId != null) {
                    headers.put(CORRELATION_ID_KEY, correlationId);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
