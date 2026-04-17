package com.booking.platform.common.exception;

import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceExceptionTest {

    private static class NotFoundException extends ServiceException {
        NotFoundException(String message) { super(message); }
        NotFoundException(String message, Throwable cause) { super(message, cause); }

        @Override
        public Status.Code getGrpcStatusCode() { return Status.Code.NOT_FOUND; }
    }

    private static class InternalException extends ServiceException {
        InternalException(String message) { super(message); }

        @Override
        public Status.Code getGrpcStatusCode() { return Status.Code.INTERNAL; }
    }

    @Test
    void getMessage_returnsConstructorMessage() {
        ServiceException ex = new NotFoundException("entity not found");

        assertThat(ex.getMessage()).isEqualTo("entity not found");
    }

    @Test
    void getGrpcStatusCode_returnsSubclassDefinedCode() {
        assertThat(new NotFoundException("x").getGrpcStatusCode()).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(new InternalException("x").getGrpcStatusCode()).isEqualTo(Status.Code.INTERNAL);
    }

    @Test
    void causeConstructor_propagatesCause() {
        RuntimeException cause = new RuntimeException("root cause");
        ServiceException ex = new NotFoundException("wrapped", cause);

        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getMessage()).isEqualTo("wrapped");
    }

    @Test
    void isRuntimeException() {
        assertThat(new NotFoundException("x")).isInstanceOf(RuntimeException.class);
    }
}
