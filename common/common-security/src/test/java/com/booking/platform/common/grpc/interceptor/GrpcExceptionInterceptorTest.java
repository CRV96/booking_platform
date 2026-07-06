package com.booking.platform.common.grpc.interceptor;

import com.booking.platform.common.exception.ServiceException;
import com.booking.platform.common.exceptions.PermissionDeniedException;
import io.grpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.AccessControlException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GrpcExceptionInterceptorTest {

    private GrpcExceptionInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new GrpcExceptionInterceptor();
    }

    // ── ServiceException subclasses ───────────────────────────────────────────

    @Test
    void onHalfClose_serviceException_closesCallWithSubclassStatusCode() {
        CapturingServerCall<Object, Object> call = new CapturingServerCall<>();

        triggerException(call, new PermissionDeniedException("denied"));

        assertThat(call.closedStatus.getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    @Test
    void onHalfClose_serviceException_includesExceptionMessageInStatus() {
        CapturingServerCall<Object, Object> call = new CapturingServerCall<>();

        triggerException(call, new PermissionDeniedException("you shall not pass"));

        assertThat(call.closedStatus.getDescription()).isEqualTo("you shall not pass");
    }

    // ── IllegalArgumentException ──────────────────────────────────────────────

    @Test
    void onHalfClose_illegalArgumentException_closesWithInvalidArgument() {
        CapturingServerCall<Object, Object> call = new CapturingServerCall<>();

        triggerException(call, new IllegalArgumentException("bad input"));

        assertThat(call.closedStatus.getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void onHalfClose_illegalArgumentException_includesMessageInStatus() {
        CapturingServerCall<Object, Object> call = new CapturingServerCall<>();

        triggerException(call, new IllegalArgumentException("field X is required"));

        assertThat(call.closedStatus.getDescription()).isEqualTo("field X is required");
    }

    // ── AccessControlException ────────────────────────────────────────────────

    @Test
    void onHalfClose_accessControlException_closesWithPermissionDenied() {
        CapturingServerCall<Object, Object> call = new CapturingServerCall<>();

        triggerException(call, new AccessControlException("forbidden"));

        assertThat(call.closedStatus.getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    // ── Unknown exception ─────────────────────────────────────────────────────

    @Test
    void onHalfClose_unknownException_closesWithInternal() {
        CapturingServerCall<Object, Object> call = new CapturingServerCall<>();

        triggerException(call, new RuntimeException("unexpected"));

        assertThat(call.closedStatus.getCode()).isEqualTo(Status.Code.INTERNAL);
    }

    @Test
    void onHalfClose_unknownException_includesMessageInStatus() {
        CapturingServerCall<Object, Object> call = new CapturingServerCall<>();

        triggerException(call, new RuntimeException("something went wrong"));

        assertThat(call.closedStatus.getDescription()).contains("something went wrong");
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void onHalfClose_noException_callIsNotClosed() {
        CapturingServerCall<Object, Object> call = new CapturingServerCall<>();
        ServerCallHandler<Object, Object> next = (c, h) -> new ServerCall.Listener<>() {};

        ServerCall.Listener<Object> listener = interceptor.interceptCall(call, new Metadata(), next);
        listener.onHalfClose();

        assertThat(call.wasClosed).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void triggerException(CapturingServerCall<Object, Object> call, RuntimeException ex) {
        ServerCallHandler<Object, Object> next = (c, h) -> new ServerCall.Listener<>() {
            @Override
            public void onHalfClose() { throw ex; }
        };
        ServerCall.Listener<Object> listener = interceptor.interceptCall(call, new Metadata(), next);
        listener.onHalfClose();
    }

    @SuppressWarnings("unchecked")
    private static MethodDescriptor<Object, Object> buildDescriptor(String fullName) {
        return MethodDescriptor.<Object, Object>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(fullName)
                .setRequestMarshaller(mock(MethodDescriptor.Marshaller.class))
                .setResponseMarshaller(mock(MethodDescriptor.Marshaller.class))
                .build();
    }

    /**
     * Concrete test double for ServerCall that captures close() arguments.
     * Using a hand-written stub avoids Mockito byte-buddy restrictions on Java 25+.
     */
    private static class CapturingServerCall<ReqT, RespT> extends ServerCall<ReqT, RespT> {
        Status closedStatus;
        Metadata closedTrailers;
        boolean wasClosed;

        @Override
        public void close(Status status, Metadata trailers) {
            wasClosed = true;
            closedStatus = status;
            closedTrailers = trailers;
        }

        @Override public boolean isCancelled() { return false; }
        @Override public void sendMessage(RespT message) {}
        @Override public void sendHeaders(Metadata headers) {}
        @Override public void request(int numMessages) {}
        @Override public Attributes getAttributes() { return Attributes.EMPTY; }

        @SuppressWarnings("unchecked")
        @Override
        public MethodDescriptor<ReqT, RespT> getMethodDescriptor() {
            return (MethodDescriptor<ReqT, RespT>) buildDescriptor("Test/Method");
        }
    }
}
