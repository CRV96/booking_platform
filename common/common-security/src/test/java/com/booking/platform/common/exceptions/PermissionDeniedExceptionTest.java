package com.booking.platform.common.exceptions;

import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionDeniedExceptionTest {

    @Test
    void getGrpcStatusCode_returnsPermissionDenied() {
        PermissionDeniedException ex = new PermissionDeniedException("not allowed");

        assertThat(ex.getGrpcStatusCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    @Test
    void getMessage_returnsConstructorMessage() {
        PermissionDeniedException ex = new PermissionDeniedException("access denied");

        assertThat(ex.getMessage()).isEqualTo("access denied");
    }

    @Test
    void isRuntimeException() {
        assertThat(new PermissionDeniedException("x")).isInstanceOf(RuntimeException.class);
    }
}
