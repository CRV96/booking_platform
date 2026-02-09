package com.booking.platform.user_service.grpc.interceptor;

import com.booking.platform.user_service.exception.UserServiceException;
import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.core.annotation.Order;

import java.security.AccessControlException;

/**
 * Global gRPC interceptor for centralized exception handling.
 * Converts application exceptions to appropriate gRPC Status codes.
 */
@Slf4j
@GrpcGlobalServerInterceptor
@Order(InterceptorOrder.EXCEPTION_HANDLING)
public class GrpcExceptionInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        ServerCall.Listener<ReqT> listener = next.startCall(call, headers);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {
            @Override
            public void onHalfClose() {
                try {
                    super.onHalfClose();
                } catch (Exception e) {
                    handleException(call, e);
                }
            }
        };
    }

    private <ReqT, RespT> void handleException(ServerCall<ReqT, RespT> call, Exception e) {
        Status status = mapExceptionToStatus(e);

        if (status.getCode() == Status.Code.INTERNAL) {
            log.error("gRPC call failed: {}", call.getMethodDescriptor().getFullMethodName(), e);
        } else {
            log.warn("gRPC call failed: {} - {}", call.getMethodDescriptor().getFullMethodName(), e.getMessage());
        }

        call.close(status, new Metadata());
    }

    private Status mapExceptionToStatus(Exception e) {
        // Use the exception hierarchy for mapping
        if (e instanceof UserServiceException userServiceException) {
            return Status.fromCode(userServiceException.getGrpcStatusCode())
                    .withDescription(e.getMessage());
        }

        if (e instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription(e.getMessage());
        }

        if (e instanceof AccessControlException) {
            return Status.PERMISSION_DENIED.withDescription(e.getMessage());
        }

        // Default to INTERNAL for unknown exceptions
        return Status.INTERNAL.withDescription("Internal server error: " + e.getMessage());
    }
}
