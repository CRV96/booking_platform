package com.booking.platform.notification_service.grpc.interceptor;

import com.booking.platform.notification_service.grpc.auth.KeycloakServiceTokenProvider;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;

@GrpcGlobalClientInterceptor
public class ServiceAuthClientInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final KeycloakServiceTokenProvider tokenProvider;

    public ServiceAuthClientInterceptor(KeycloakServiceTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(AUTHORIZATION_KEY, "Bearer " + tokenProvider.getToken());
                super.start(responseListener, headers);
            }
        };
    }
}
