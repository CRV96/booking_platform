package com.booking.platform.common.grpc.interceptor;

import com.booking.platform.common.grpc.context.GrpcUserContext;
import io.grpc.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtPropagationClientInterceptorTest {

    @Mock private Channel channel;
    @Mock private ClientCall<Object, Object> clientCall;
    @Mock private ClientCall.Listener<Object> listener;

    private final JwtPropagationClientInterceptor interceptor = new JwtPropagationClientInterceptor();

    private Context.CancellableContext grpcCtx;
    private Context previousContext;

    @AfterEach
    void detach() {
        if (grpcCtx != null) {
            grpcCtx.detach(previousContext);
            grpcCtx.cancel(null);
            grpcCtx = null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ClientCall<Object, Object> intercept() {
        MethodDescriptor<Object, Object> method = mock(MethodDescriptor.class);
        when(channel.newCall(any(MethodDescriptor.class), any(CallOptions.class)))
                .thenReturn((ClientCall) clientCall);
        return interceptor.interceptCall(method, CallOptions.DEFAULT, channel);
    }

    private void attachJwt(String token) {
        grpcCtx = Context.current().withValue(GrpcUserContext.JWT_TOKEN, token).withCancellation();
        previousContext = grpcCtx.attach();
    }

    // ── JWT in context ────────────────────────────────────────────────────────

    @Test
    void jwtInContext_addsAuthorizationHeader() {
        attachJwt("my-token");

        ClientCall<Object, Object> wrapped = intercept();
        ArgumentCaptor<Metadata> headersCaptor = ArgumentCaptor.forClass(Metadata.class);
        wrapped.start(listener, new Metadata());

        verify(clientCall).start(any(), headersCaptor.capture());
        Metadata.Key<String> authKey =
                Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
        assertThat(headersCaptor.getValue().get(authKey)).isEqualTo("Bearer my-token");
    }

    @Test
    void jwtInContext_prefixesBearerScheme() {
        attachJwt("abc.def.ghi");

        ClientCall<Object, Object> wrapped = intercept();
        ArgumentCaptor<Metadata> headersCaptor = ArgumentCaptor.forClass(Metadata.class);
        wrapped.start(listener, new Metadata());

        verify(clientCall).start(any(), headersCaptor.capture());
        Metadata.Key<String> authKey =
                Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
        String value = headersCaptor.getValue().get(authKey);
        assertThat(value).startsWith("Bearer ").endsWith("abc.def.ghi");
    }

    // ── No JWT ────────────────────────────────────────────────────────────────

    @Test
    void noJwtInContext_doesNotAddHeader() {
        ClientCall<Object, Object> wrapped = intercept();
        ArgumentCaptor<Metadata> headersCaptor = ArgumentCaptor.forClass(Metadata.class);
        wrapped.start(listener, new Metadata());

        verify(clientCall).start(any(), headersCaptor.capture());
        Metadata.Key<String> authKey =
                Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
        assertThat(headersCaptor.getValue().get(authKey)).isNull();
    }

    @Test
    void noJwtInContext_callStillProceedsToChannel() {
        ClientCall<Object, Object> wrapped = intercept();
        wrapped.start(listener, new Metadata());

        verify(clientCall).start(any(), any());
    }
}
