package com.booking.platform.user_service.exception.user;

import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserNotFoundExceptionTest {

    @Test
    void forId_containsUserIdInMessage() {
        UserNotFoundException ex = UserNotFoundException.forId("user-123");
        assertThat(ex.getMessage()).contains("user-123");
    }

    @Test
    void forUsername_containsUsernameInMessage() {
        UserNotFoundException ex = UserNotFoundException.forUsername("john.doe");
        assertThat(ex.getMessage()).contains("john.doe");
    }

    @Test
    void forEmail_containsEmailInMessage() {
        UserNotFoundException ex = UserNotFoundException.forEmail("john@example.com");
        assertThat(ex.getMessage()).contains("john@example.com");
    }

    @Test
    void getGrpcStatusCode_returnsNotFound() {
        assertThat(UserNotFoundException.forId("any").getGrpcStatusCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void isRuntimeException() {
        assertThat(UserNotFoundException.forId("x")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void directConstructor_setsMessage() {
        UserNotFoundException ex = new UserNotFoundException("custom message");
        assertThat(ex.getMessage()).isEqualTo("custom message");
    }
}
