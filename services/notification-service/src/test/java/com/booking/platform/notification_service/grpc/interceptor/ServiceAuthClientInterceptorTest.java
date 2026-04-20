package com.booking.platform.notification_service.grpc.interceptor;

import com.booking.platform.notification_service.grpc.auth.KeycloakServiceTokenProvider;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceAuthClientInterceptorTest {

    @Mock private KeycloakServiceTokenProvider tokenProvider;
    @Mock private Channel channel;
    @Mock private ClientCall<Object, Object> clientCall;
    @Mock private ClientCall.Listener<Object> listener;

    @InjectMocks private ServiceAuthClientInterceptor interceptor;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void interceptCall_injectsAuthorizationHeader() {
        when(tokenProvider.getToken()).thenReturn("my-token");
        when(channel.newCall(any(MethodDescriptor.class), any(CallOptions.class)))
                .thenReturn((ClientCall) clientCall);
        MethodDescriptor<Object, Object> method = mock(MethodDescriptor.class);

        ClientCall<Object, Object> wrappedCall =
                interceptor.interceptCall(method, CallOptions.DEFAULT, channel);

        ArgumentCaptor<Metadata> headersCaptor = ArgumentCaptor.forClass(Metadata.class);
        wrappedCall.start(listener, new Metadata());

        verify(clientCall).start(eq(listener), headersCaptor.capture());
        Metadata.Key<String> authKey =
                Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
        assertThat(headersCaptor.getValue().get(authKey)).isEqualTo("Bearer my-token");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void interceptCall_callsTokenProviderForEachCall() {
        when(tokenProvider.getToken()).thenReturn("tok-1").thenReturn("tok-2");
        when(channel.newCall(any(MethodDescriptor.class), any(CallOptions.class)))
                .thenReturn((ClientCall) clientCall);
        MethodDescriptor<Object, Object> method = mock(MethodDescriptor.class);

        ClientCall<Object, Object> call1 = interceptor.interceptCall(method, CallOptions.DEFAULT, channel);
        ClientCall<Object, Object> call2 = interceptor.interceptCall(method, CallOptions.DEFAULT, channel);
        call1.start(listener, new Metadata());
        call2.start(listener, new Metadata());

        verify(tokenProvider, times(2)).getToken();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void interceptCall_prefixesBearerScheme() {
        when(tokenProvider.getToken()).thenReturn("abc123");
        when(channel.newCall(any(MethodDescriptor.class), any(CallOptions.class)))
                .thenReturn((ClientCall) clientCall);
        MethodDescriptor<Object, Object> method = mock(MethodDescriptor.class);

        ClientCall<Object, Object> wrappedCall =
                interceptor.interceptCall(method, CallOptions.DEFAULT, channel);

        ArgumentCaptor<Metadata> headersCaptor = ArgumentCaptor.forClass(Metadata.class);
        wrappedCall.start(listener, new Metadata());

        verify(clientCall).start(any(), headersCaptor.capture());
        Metadata.Key<String> authKey =
                Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
        String headerValue = headersCaptor.getValue().get(authKey);
        assertThat(headerValue).startsWith("Bearer ");
        assertThat(headerValue).endsWith("abc123");
    }
}
