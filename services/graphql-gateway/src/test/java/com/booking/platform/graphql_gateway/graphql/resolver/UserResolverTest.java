package com.booking.platform.graphql_gateway.graphql.resolver;

import com.booking.platform.common.grpc.user.SearchUsersResponse;
import com.booking.platform.common.grpc.user.UserInfo;
import com.booking.platform.graphql_gateway.dto.user.UpdateProfileInput;
import com.booking.platform.graphql_gateway.dto.user.User;
import com.booking.platform.graphql_gateway.dto.user.UserConnection;
import com.booking.platform.graphql_gateway.exception.ErrorCode;
import com.booking.platform.graphql_gateway.exception.GraphQLException;
import com.booking.platform.graphql_gateway.grpc.client.UserOperationsClient;
import com.booking.platform.graphql_gateway.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserResolverTest {

    @Mock private UserOperationsClient userOperationsClient;
    @Mock private AuthService authService;

    @InjectMocks private UserResolver resolver;

    private static final UserInfo USER_INFO = UserInfo.newBuilder()
            .setId("u-1").setUsername("alice").setEmail("alice@test.com")
            .build();

    // ── me (authenticated query) ──────────────────────────────────────────────

    @Test
    void me_fetchesCurrentUser() {
        when(authService.getAuthenticatedUserId()).thenReturn("u-1");
        when(userOperationsClient.getUser("u-1")).thenReturn(USER_INFO);

        User result = resolver.me();

        verify(authService).getAuthenticatedUserId();
        verify(userOperationsClient).getUser("u-1");
        assertThat(result.id()).isEqualTo("u-1");
        assertThat(result.email()).isEqualTo("alice@test.com");
    }

    // ── user (admin only) ─────────────────────────────────────────────────────

    @Test
    void user_requiresAdminRole() {
        doThrow(new GraphQLException(ErrorCode.FORBIDDEN))
                .when(authService).requireRole("admin");

        assertThatThrownBy(() -> resolver.user("u-2"))
                .isInstanceOf(GraphQLException.class)
                .extracting(ex -> ((GraphQLException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(userOperationsClient);
    }

    @Test
    void user_whenAuthorized_fetchesById() {
        when(userOperationsClient.getUser("u-2")).thenReturn(USER_INFO);

        User result = resolver.user("u-2");

        verify(authService).requireRole("admin");
        verify(userOperationsClient).getUser("u-2");
        assertThat(result.id()).isEqualTo("u-1");
    }

    // ── users (admin only) ────────────────────────────────────────────────────

    @Test
    void users_requiresAdminRole() {
        doThrow(new GraphQLException(ErrorCode.FORBIDDEN))
                .when(authService).requireRole("admin");

        assertThatThrownBy(() -> resolver.users(null, null, null))
                .isInstanceOf(GraphQLException.class);

        verifyNoInteractions(userOperationsClient);
    }

    @Test
    void users_defaultsPageAndPageSize() {
        when(userOperationsClient.searchUsers(any(), anyInt(), anyInt()))
                .thenReturn(SearchUsersResponse.getDefaultInstance());

        resolver.users(null, null, null);

        verify(userOperationsClient).searchUsers(null, 0, 20);
    }

    @Test
    void users_passesExplicitPaging() {
        when(userOperationsClient.searchUsers(any(), anyInt(), anyInt()))
                .thenReturn(SearchUsersResponse.getDefaultInstance());

        resolver.users("alice", 1, 10);

        verify(userOperationsClient).searchUsers("alice", 1, 10);
    }

    @Test
    void users_returnsConnection() {
        when(userOperationsClient.searchUsers(any(), anyInt(), anyInt()))
                .thenReturn(SearchUsersResponse.getDefaultInstance());

        UserConnection conn = resolver.users(null, null, null);

        assertThat(conn).isNotNull();
        assertThat(conn.users()).isEmpty();
    }

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Test
    void updateProfile_usesAuthenticatedUserId() {
        when(authService.getAuthenticatedUserId()).thenReturn("u-5");
        UpdateProfileInput input = new UpdateProfileInput(
                "Bob", "Jones", "bob@test.com", null, null, null, null, null, null, null, null);
        when(userOperationsClient.updateUser(anyString(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenReturn(USER_INFO);

        resolver.updateProfile(input);

        verify(authService).getAuthenticatedUserId();
        verify(userOperationsClient).updateUser(eq("u-5"), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateProfile_mapsInputFieldsToClient() {
        when(authService.getAuthenticatedUserId()).thenReturn("u-1");
        UpdateProfileInput input = new UpdateProfileInput(
                "Carol", "White", "carol@test.com", "+44999", "GB",
                "en", "GBP", "Europe/London", "https://pic.url", true, false);
        when(userOperationsClient.updateUser(anyString(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenReturn(USER_INFO);

        resolver.updateProfile(input);

        verify(userOperationsClient).updateUser(
                "u-1", "Carol", "White", "carol@test.com", "+44999", "GB",
                "en", "GBP", "Europe/London", "https://pic.url", true, false);
    }
}
