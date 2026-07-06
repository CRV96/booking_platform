package com.booking.platform.user_service.exception.auth;

import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthExceptionsTest {

    @Test
    void authenticationException_returnsUnauthenticated() {
        assertThat(new AuthenticationException("auth failed").getGrpcStatusCode())
                .isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void authenticationException_withCause_preservesCause() {
        RuntimeException cause = new RuntimeException("upstream");
        AuthenticationException ex = new AuthenticationException("wrapped", cause);
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void invalidCredentialsException_extendsAuthenticationException() {
        assertThat(new InvalidCredentialsException("bad creds"))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void invalidCredentialsException_inheritsUnauthenticatedStatus() {
        assertThat(new InvalidCredentialsException("bad creds").getGrpcStatusCode())
                .isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void invalidTokenException_extendsAuthenticationException() {
        assertThat(new InvalidTokenException("bad token"))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void invalidTokenException_inheritsUnauthenticatedStatus() {
        assertThat(new InvalidTokenException("bad token").getGrpcStatusCode())
                .isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void allAuthExceptionsAreRuntimeExceptions() {
        assertThat(new AuthenticationException("x")).isInstanceOf(RuntimeException.class);
        assertThat(new InvalidCredentialsException("x")).isInstanceOf(RuntimeException.class);
        assertThat(new InvalidTokenException("x")).isInstanceOf(RuntimeException.class);
    }
}
