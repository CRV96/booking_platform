package com.booking.platform.common.grpc.interceptor;

import com.booking.platform.common.grpc.context.GrpcUserContext;
import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;

/**
 * gRPC client interceptor that propagates the JWT token from the incoming gRPC context
 * to outgoing gRPC calls (service-to-service propagation).
 *
 * <p>When booking-service receives an authenticated gRPC call from the gateway, the
 * server-side {@link com.booking.platform.common.grpc.interceptor.JwtContextInterceptor}
 * stores the JWT in {@link GrpcUserContext#JWT_TOKEN}. This interceptor reads that token
 * and attaches it to any outbound gRPC calls (e.g., to event-service), so the downstream
 * service can authenticate the request.</p>
 */
@Slf4j
@GrpcGlobalClientInterceptor
public class JwtPropagationClientInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String jwtToken = GrpcUserContext.getJwtToken();

                if (jwtToken != null) {
                    headers.put(AUTHORIZATION_KEY, "Bearer " + jwtToken);
                    log.debug("Propagating JWT to gRPC call: {}", method.getFullMethodName());
                }

                super.start(responseListener, headers);
            }
        };
    }
}
