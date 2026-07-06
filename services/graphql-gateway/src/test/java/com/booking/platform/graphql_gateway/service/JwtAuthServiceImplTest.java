package com.booking.platform.graphql_gateway.service;

import com.booking.platform.graphql_gateway.exception.ErrorCode;
import com.booking.platform.graphql_gateway.exception.GraphQLException;
import com.booking.platform.graphql_gateway.service.impl.JwtAuthServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAuthServiceImplTest {

    private final JwtAuthServiceImpl authService = new JwtAuthServiceImpl();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void setJwt(String userId, String... roles) {
        Jwt jwt = Jwt.withTokenValue("token-value")
                .header("alg", "RS256")
                .claim("sub", userId)
                .build();
        List<SimpleGrantedAuthority> authorities = List.of(roles).stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities));
    }

    private void setJwtNoSub() {
        Jwt jwt = Jwt.withTokenValue("token-value")
                .header("alg", "RS256")
                .claim("email", "x@x.com")  // has email but no sub
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    // ── getAuthenticatedUserId ────────────────────────────────────────────────

    @Test
    void getAuthenticatedUserId_validJwt_returnsSubject() {
        setJwt("user-99");

        assertThat(authService.getAuthenticatedUserId()).isEqualTo("user-99");
    }

    @Test
    void getAuthenticatedUserId_noAuthentication_throwsUnauthenticated() {
        assertThatThrownBy(authService::getAuthenticatedUserId)
                .isInstanceOf(GraphQLException.class)
                .extracting(ex -> ((GraphQLException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void getAuthenticatedUserId_jwtMissingSubClaim_throwsUnauthenticated() {
        setJwtNoSub();

        assertThatThrownBy(authService::getAuthenticatedUserId)
                .isInstanceOf(GraphQLException.class)
                .extracting(ex -> ((GraphQLException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    // ── isAuthenticated ───────────────────────────────────────────────────────

    @Test
    void isAuthenticated_validJwt_returnsTrue() {
        setJwt("user-1");

        assertThat(authService.isAuthenticated()).isTrue();
    }

    @Test
    void isAuthenticated_noAuthentication_returnsFalse() {
        assertThat(authService.isAuthenticated()).isFalse();
    }

    @Test
    void isAuthenticated_jwtMissingSubClaim_returnsFalse() {
        setJwtNoSub();

        assertThat(authService.isAuthenticated()).isFalse();
    }

    // ── hasRole ───────────────────────────────────────────────────────────────

    @Test
    void hasRole_userHasRole_returnsTrue() {
        setJwt("user-1", "employee");

        assertThat(authService.hasRole("employee")).isTrue();
    }

    @Test
    void hasRole_userDoesNotHaveRole_returnsFalse() {
        setJwt("user-1", "customer");

        assertThat(authService.hasRole("admin")).isFalse();
    }

    @Test
    void hasRole_noAuthentication_returnsFalse() {
        assertThat(authService.hasRole("employee")).isFalse();
    }

    @Test
    void hasRole_userHasMultipleRoles_matchesCorrectOne() {
        setJwt("user-1", "customer", "employee");

        assertThat(authService.hasRole("employee")).isTrue();
        assertThat(authService.hasRole("admin")).isFalse();
    }

    // ── requireRole ───────────────────────────────────────────────────────────

    @Test
    void requireRole_userHasRole_doesNotThrow() {
        setJwt("user-1", "admin");

        authService.requireRole("admin"); // no exception
    }

    @Test
    void requireRole_noAuthentication_throwsUnauthenticated() {
        assertThatThrownBy(() -> authService.requireRole("admin"))
                .isInstanceOf(GraphQLException.class)
                .extracting(ex -> ((GraphQLException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void requireRole_authenticatedButWrongRole_throwsForbidden() {
        setJwt("user-1", "customer");

        assertThatThrownBy(() -> authService.requireRole("admin"))
                .isInstanceOf(GraphQLException.class)
                .extracting(ex -> ((GraphQLException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }
}
