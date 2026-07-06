package com.booking.platform.common.grpc.context;

import io.grpc.Context;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcUserContextTest {

    // ── Accessors outside a populated context ─────────────────────────────────

    @Test
    void getUserId_returnsNullOutsideContext() {
        assertThat(GrpcUserContext.getUserId()).isNull();
    }

    @Test
    void getRoles_returnsEmptyListWhenNotSet() {
        assertThat(GrpcUserContext.getRoles()).isEmpty();
    }

    @Test
    void isAuthenticated_returnsFalseWhenUserIdNotSet() {
        assertThat(GrpcUserContext.isAuthenticated()).isFalse();
    }

    @Test
    void hasRole_returnsFalseWhenNoRolesInContext() {
        assertThat(GrpcUserContext.hasRole("employee")).isFalse();
    }

    // ── Accessors inside a populated context ──────────────────────────────────

    @Test
    void getUserId_returnsValueFromContext() {
        withContext("user-42", "alice", "alice@example.com", List.of("customer"), () ->
                assertThat(GrpcUserContext.getUserId()).isEqualTo("user-42"));
    }

    @Test
    void getUsername_returnsValueFromContext() {
        withContext("user-42", "alice", "alice@example.com", List.of("customer"), () ->
                assertThat(GrpcUserContext.getUsername()).isEqualTo("alice"));
    }

    @Test
    void getEmail_returnsValueFromContext() {
        withContext("user-42", "alice", "alice@example.com", List.of("customer"), () ->
                assertThat(GrpcUserContext.getEmail()).isEqualTo("alice@example.com"));
    }

    @Test
    void getRoles_returnsRolesFromContext() {
        withContext("user-42", "alice", "alice@example.com", List.of("customer", "employee"), () ->
                assertThat(GrpcUserContext.getRoles()).containsExactly("customer", "employee"));
    }

    @Test
    void isAuthenticated_returnsTrueWhenUserIdSet() {
        withContext("user-42", "alice", "alice@example.com", List.of(), () ->
                assertThat(GrpcUserContext.isAuthenticated()).isTrue());
    }

    @Test
    void hasRole_returnsTrueWhenRolePresent() {
        withContext("user-42", "alice", "alice@example.com", List.of("customer", "employee"), () ->
                assertThat(GrpcUserContext.hasRole("employee")).isTrue());
    }

    @Test
    void hasRole_returnsFalseWhenRoleAbsent() {
        withContext("user-42", "alice", "alice@example.com", List.of("customer"), () ->
                assertThat(GrpcUserContext.hasRole("admin")).isFalse());
    }

    @Test
    void getJwtExpiry_returnsValueFromContext() {
        Instant expiry = Instant.parse("2030-01-01T00:00:00Z");
        Context ctx = Context.current()
                .withValue(GrpcUserContext.USER_ID, "u1")
                .withValue(GrpcUserContext.JWT_EXPIRY, expiry);
        Context prev = ctx.attach();
        try {
            assertThat(GrpcUserContext.getJwtExpiry()).isEqualTo(expiry);
        } finally {
            ctx.detach(prev);
        }
    }

    @Test
    void getJwtToken_returnsValueFromContext() {
        Context ctx = Context.current()
                .withValue(GrpcUserContext.USER_ID, "u1")
                .withValue(GrpcUserContext.JWT_TOKEN, "eyJhbGci.payload.sig");
        Context prev = ctx.attach();
        try {
            assertThat(GrpcUserContext.getJwtToken()).isEqualTo("eyJhbGci.payload.sig");
        } finally {
            ctx.detach(prev);
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private void withContext(String userId, String username, String email,
                             List<String> roles, Runnable assertion) {
        Context ctx = Context.current()
                .withValue(GrpcUserContext.USER_ID, userId)
                .withValue(GrpcUserContext.USERNAME, username)
                .withValue(GrpcUserContext.EMAIL, email)
                .withValue(GrpcUserContext.ROLES, roles);
        Context prev = ctx.attach();
        try {
            assertion.run();
        } finally {
            ctx.detach(prev);
        }
    }
}
