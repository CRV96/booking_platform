package com.booking.platform.user_service.exception;

import com.booking.platform.user_service.exception.user.UserAlreadyExistsException;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionStatusTest {

    @Test
    void validationException_returnsInvalidArgument() {
        assertThat(new ValidationException("bad input").getGrpcStatusCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void validationException_isRuntimeException() {
        assertThat(new ValidationException("x")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void internalException_returnsInternal() {
        assertThat(new InternalException("server error").getGrpcStatusCode())
                .isEqualTo(Status.Code.INTERNAL);
    }

    @Test
    void internalException_isRuntimeException() {
        assertThat(new InternalException("x")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void userAlreadyExistsException_returnsAlreadyExists() {
        assertThat(new UserAlreadyExistsException("already exists").getGrpcStatusCode())
                .isEqualTo(Status.Code.ALREADY_EXISTS);
    }

    @Test
    void userAlreadyExistsException_isRuntimeException() {
        assertThat(new UserAlreadyExistsException("x")).isInstanceOf(RuntimeException.class);
    }
}
