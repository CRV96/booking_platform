package com.booking.platform.user_service.grpc.server;

import com.booking.platform.common.grpc.user.*;
import com.booking.platform.user_service.mapper.AttributeMapper;
import com.booking.platform.user_service.mapper.UserGrpcMapper;
import com.booking.platform.user_service.properties.ValidationProperties;
import com.booking.platform.user_service.service.KeycloakUserService;
import com.booking.platform.user_service.validation.UserValidator;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserGrpcServiceTest {

    @Mock private KeycloakUserService keycloakUserService;
    @Mock private UserGrpcMapper userGrpcMapper;
    @Mock private AttributeMapper attributeMapper;
    @Mock private UserValidator userValidator;
    @Mock private StreamObserver<UserResponse> responseObserver;
    @Mock private StreamObserver<SearchUsersResponse> searchObserver;
    @Mock private StreamObserver<UserEmailResponse> emailObserver;
    @Mock private StreamObserver<GetUsersEmailsResponse> usersEmailsObserver;

    private ValidationProperties validationProperties;
    private UserGrpcService service;

    @BeforeEach
    void setUp() {
        // minPageSize=1, maxPageSize=100; other fields use compact constructor defaults
        validationProperties = new ValidationProperties(null, 0, 0, 0, 0, 1, 100);
        service = new UserGrpcService(keycloakUserService, userGrpcMapper, attributeMapper, userValidator, validationProperties);

        // Common stubs reused across multiple tests
        when(keycloakUserService.getUserRoles(any())).thenReturn(List.of("customer"));
        when(userGrpcMapper.toUserInfo(any(), any())).thenReturn(UserInfo.getDefaultInstance());
    }

    // ── getUser ───────────────────────────────────────────────────────────────

    @Test
    void getUser_validatesIdAndFetchesUser() {
        UserRepresentation user = makeUser("user-1", "a@b.com");
        when(keycloakUserService.getUserById("user-1")).thenReturn(user);

        GetUserRequest request = GetUserRequest.newBuilder().setUserId("user-1").build();
        service.getUser(request, responseObserver);

        verify(userValidator).validateUserId("user-1");
        verify(keycloakUserService).getUserById("user-1");
        verify(keycloakUserService).getUserRoles("user-1");
        verify(userGrpcMapper).toUserInfo(eq(user), anyList());
        verify(responseObserver).onNext(any(UserResponse.class));
        verify(responseObserver).onCompleted();
    }

    @Test
    void getUser_buildsResponseWithUserInfo() {
        UserInfo userInfo = UserInfo.newBuilder().setId("user-1").setEmail("a@b.com").build();
        UserRepresentation user = makeUser("user-1", "a@b.com");
        when(keycloakUserService.getUserById("user-1")).thenReturn(user);
        when(userGrpcMapper.toUserInfo(eq(user), anyList())).thenReturn(userInfo);

        GetUserRequest request = GetUserRequest.newBuilder().setUserId("user-1").build();
        service.getUser(request, responseObserver);

        verify(responseObserver).onNext(argThat(resp -> resp.getUser().getId().equals("user-1")));
        verify(responseObserver).onCompleted();
    }

    // ── getUserByUsername ─────────────────────────────────────────────────────

    @Test
    void getUserByUsername_validatesUsernameAndFetchesUser() {
        UserRepresentation user = makeUser("user-1", "a@b.com");
        when(keycloakUserService.getUserByUsername("john")).thenReturn(user);

        GetUserByUsernameRequest request = GetUserByUsernameRequest.newBuilder().setUsername("john").build();
        service.getUserByUsername(request, responseObserver);

        verify(userValidator).validateUsername("john");
        verify(keycloakUserService).getUserByUsername("john");
        verify(keycloakUserService).getUserRoles("user-1");
        verify(responseObserver).onNext(any(UserResponse.class));
        verify(responseObserver).onCompleted();
    }

    // ── getUserByEmail ────────────────────────────────────────────────────────

    @Test
    void getUserByEmail_validatesEmailAndFetchesUser() {
        UserRepresentation user = makeUser("user-1", "a@b.com");
        when(keycloakUserService.getUserByEmail("a@b.com")).thenReturn(user);

        GetUserByEmailRequest request = GetUserByEmailRequest.newBuilder().setEmail("a@b.com").build();
        service.getUserByEmail(request, responseObserver);

        verify(userValidator).validateEmail("a@b.com");
        verify(keycloakUserService).getUserByEmail("a@b.com");
        verify(keycloakUserService).getUserRoles("user-1");
        verify(responseObserver).onNext(any(UserResponse.class));
        verify(responseObserver).onCompleted();
    }

    // ── updateUser ────────────────────────────────────────────────────────────

    @Test
    void updateUser_withFirstName_passesFirstNameToService() {
        UserRepresentation updated = makeUser("u-1", "a@b.com");
        when(attributeMapper.fromUpdateRequest(any())).thenReturn(Map.of());
        when(keycloakUserService.updateUser(anyString(), anyString(), any(), any(), any())).thenReturn(updated);

        UpdateUserRequest request = UpdateUserRequest.newBuilder()
                .setUserId("u-1")
                .setFirstName("John")
                .build();
        service.updateUser(request, responseObserver);

        verify(userValidator).validateUpdateUserRequest(request);
        verify(attributeMapper).fromUpdateRequest(request);
        verify(keycloakUserService).updateUser(eq("u-1"), eq("John"), isNull(), isNull(), any());
        verify(keycloakUserService).getUserRoles("u-1");
        verify(responseObserver).onNext(any(UserResponse.class));
        verify(responseObserver).onCompleted();
    }

    @Test
    void updateUser_withoutFirstName_passesNullFirstNameToService() {
        UserRepresentation updated = makeUser("u-1", "a@b.com");
        when(attributeMapper.fromUpdateRequest(any())).thenReturn(Map.of());
        when(keycloakUserService.updateUser(anyString(), isNull(), isNull(), isNull(), any())).thenReturn(updated);

        UpdateUserRequest request = UpdateUserRequest.newBuilder()
                .setUserId("u-1")
                .build();

        assertThat(request.hasFirstName()).isFalse();

        service.updateUser(request, responseObserver);

        verify(keycloakUserService).updateUser(eq("u-1"), isNull(), isNull(), isNull(), any());
        verify(responseObserver).onNext(any(UserResponse.class));
        verify(responseObserver).onCompleted();
    }

    // ── searchUsers ───────────────────────────────────────────────────────────

    @Test
    void searchUsers_clampsTooSmallPageSizeToMin() {
        when(keycloakUserService.searchUsers(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(keycloakUserService.getUserCount(any())).thenReturn(0);
        when(keycloakUserService.getUsersRoles(any())).thenReturn(Map.of());

        SearchUsersRequest request = SearchUsersRequest.newBuilder()
                .setPage(0)
                .setPageSize(0) // below min → clamped to 1
                .build();
        service.searchUsers(request, searchObserver);

        // pageSize=0 → clamp to 1; searchUsers called with pageSize=1
        verify(keycloakUserService).searchUsers(isNull(), eq(0), eq(1));
    }

    @Test
    void searchUsers_clampsTooLargePageSizeToMax() {
        when(keycloakUserService.searchUsers(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(keycloakUserService.getUserCount(any())).thenReturn(0);
        when(keycloakUserService.getUsersRoles(any())).thenReturn(Map.of());

        SearchUsersRequest request = SearchUsersRequest.newBuilder()
                .setPage(0)
                .setPageSize(200) // above max → clamped to 100
                .build();
        service.searchUsers(request, searchObserver);

        verify(keycloakUserService).searchUsers(isNull(), eq(0), eq(100));
    }

    @Test
    void searchUsers_withQuery_passesQueryToService() {
        when(keycloakUserService.searchUsers(eq("alice"), anyInt(), anyInt())).thenReturn(List.of());
        when(keycloakUserService.getUserCount(eq("alice"))).thenReturn(0);
        when(keycloakUserService.getUsersRoles(any())).thenReturn(Map.of());

        SearchUsersRequest request = SearchUsersRequest.newBuilder()
                .setQuery("alice")
                .setPage(0)
                .setPageSize(10)
                .build();
        service.searchUsers(request, searchObserver);

        verify(keycloakUserService).searchUsers(eq("alice"), eq(0), eq(10));
        verify(keycloakUserService).getUserCount(eq("alice"));
    }

    @Test
    void searchUsers_calculatesTotalPagesCorrectly() {
        UserRepresentation u1 = makeUser("u1", "a@b.com");
        UserRepresentation u2 = makeUser("u2", "b@c.com");
        when(keycloakUserService.searchUsers(any(), anyInt(), anyInt())).thenReturn(List.of(u1, u2));
        when(keycloakUserService.getUserCount(any())).thenReturn(25);
        when(keycloakUserService.getUsersRoles(any())).thenReturn(Map.of());

        SearchUsersRequest request = SearchUsersRequest.newBuilder()
                .setPage(0)
                .setPageSize(10)
                .build();
        service.searchUsers(request, searchObserver);

        // totalPages = ceil(25/10) = 3
        verify(searchObserver).onNext(argThat(resp ->
                resp.getTotalCount() == 25 && resp.getTotalPages() == 3 && resp.getPageSize() == 10
        ));
        verify(searchObserver).onCompleted();
    }

    @Test
    void searchUsers_fetchesRolesForAllUsers() {
        UserRepresentation u1 = makeUser("u1", "a@b.com");
        UserRepresentation u2 = makeUser("u2", "b@c.com");
        Map<String, List<String>> rolesMap = Map.of("u1", List.of("customer"), "u2", List.of("customer"));
        when(keycloakUserService.searchUsers(any(), anyInt(), anyInt())).thenReturn(List.of(u1, u2));
        when(keycloakUserService.getUserCount(any())).thenReturn(2);
        when(keycloakUserService.getUsersRoles(List.of("u1", "u2"))).thenReturn(rolesMap);

        SearchUsersRequest request = SearchUsersRequest.newBuilder()
                .setPage(0)
                .setPageSize(10)
                .build();
        service.searchUsers(request, searchObserver);

        verify(keycloakUserService).getUsersRoles(List.of("u1", "u2"));
        verify(searchObserver).onCompleted();
    }

    // ── getUserEmail ──────────────────────────────────────────────────────────

    @Test
    void getUserEmail_whenUserNotFound_callsOnError() {
        when(keycloakUserService.getUserById("missing")).thenReturn(null);

        GetUserEmailRequest request = GetUserEmailRequest.newBuilder().setUserId("missing").build();
        service.getUserEmail(request, emailObserver);

        verify(emailObserver).onError(any(IllegalArgumentException.class));
        verify(emailObserver, never()).onNext(any());
        verify(emailObserver, never()).onCompleted();
    }

    @Test
    void getUserEmail_whenUserExists_returnsEmail() {
        UserRepresentation user = makeUser("user-1", "a@b.com");
        when(keycloakUserService.getUserById("user-1")).thenReturn(user);

        GetUserEmailRequest request = GetUserEmailRequest.newBuilder().setUserId("user-1").build();
        service.getUserEmail(request, emailObserver);

        verify(emailObserver).onNext(argThat(resp -> resp.getEmail().equals("a@b.com")));
        verify(emailObserver).onCompleted();
        verify(emailObserver, never()).onError(any());
    }

    // ── getUsersEmails ────────────────────────────────────────────────────────

    @Test
    void getUsersEmails_returnsEmailsForAllUsers() {
        UserRepresentation u1 = makeUser("u1", "a@b.com");
        UserRepresentation u2 = makeUser("u2", "c@d.com");
        when(keycloakUserService.getUsersByIds(List.of("u1", "u2"))).thenReturn(List.of(u1, u2));

        GetUsersEmailsRequest request = GetUsersEmailsRequest.newBuilder()
                .addUserIds("u1")
                .addUserIds("u2")
                .build();
        service.getUsersEmails(request, usersEmailsObserver);

        verify(keycloakUserService).getUsersByIds(List.of("u1", "u2"));
        verify(usersEmailsObserver).onNext(argThat(resp ->
                resp.getUserEmailsList().contains("a@b.com") &&
                resp.getUserEmailsList().contains("c@d.com")
        ));
        verify(usersEmailsObserver).onCompleted();
    }

    @Test
    void getUsersEmails_emptyList_returnsEmptyResponse() {
        when(keycloakUserService.getUsersByIds(List.of())).thenReturn(List.of());

        GetUsersEmailsRequest request = GetUsersEmailsRequest.newBuilder().build();
        service.getUsersEmails(request, usersEmailsObserver);

        verify(usersEmailsObserver).onNext(argThat(resp -> resp.getUserEmailsList().isEmpty()));
        verify(usersEmailsObserver).onCompleted();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UserRepresentation makeUser(String id, String email) {
        UserRepresentation user = new UserRepresentation();
        user.setId(id);
        user.setEmail(email);
        user.setUsername(email);
        return user;
    }
}
