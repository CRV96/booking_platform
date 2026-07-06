package com.booking.platform.graphql_gateway.grpc.client.impl;

import com.booking.platform.common.grpc.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceClientImplTest {

    @Mock private UserServiceGrpc.UserServiceBlockingStub stub;

    private UserServiceClientImpl client;

    private final UserInfo defaultUser = UserInfo.newBuilder()
            .setId("u-1").setEmail("alice@test.com").build();

    @BeforeEach
    void setUp() {
        client = new UserServiceClientImpl();
        ReflectionTestUtils.setField(client, "userServiceStub", stub);
        when(stub.getUser(any())).thenReturn(UserResponse.newBuilder().setUser(defaultUser).build());
        when(stub.getUserByUsername(any())).thenReturn(UserResponse.newBuilder().setUser(defaultUser).build());
        when(stub.updateUser(any())).thenReturn(UserResponse.newBuilder().setUser(defaultUser).build());
        when(stub.searchUsers(any())).thenReturn(SearchUsersResponse.getDefaultInstance());
    }

    // ── getUser ───────────────────────────────────────────────────────────────

    @Test
    void getUser_sendsCorrectUserId() {
        client.getUser("u-1");

        ArgumentCaptor<GetUserRequest> captor = ArgumentCaptor.forClass(GetUserRequest.class);
        verify(stub).getUser(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("u-1");
    }

    @Test
    void getUser_returnsUserFromResponse() {
        UserInfo result = client.getUser("u-1");
        assertThat(result).isEqualTo(defaultUser);
    }

    // ── getUserByUsername ─────────────────────────────────────────────────────

    @Test
    void getUserByUsername_sendsCorrectUsername() {
        client.getUserByUsername("alice");

        ArgumentCaptor<GetUserByUsernameRequest> captor = ArgumentCaptor.forClass(GetUserByUsernameRequest.class);
        verify(stub).getUserByUsername(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("alice");
    }

    // ── updateUser ────────────────────────────────────────────────────────────

    @Test
    void updateUser_allFieldsNonNull_setsAllOnRequest() {
        client.updateUser("u-1", "John", "Doe", "j@d.com",
                "+1234", "US", "en", "USD", "UTC", "http://pic", true, false);

        ArgumentCaptor<UpdateUserRequest> captor = ArgumentCaptor.forClass(UpdateUserRequest.class);
        verify(stub).updateUser(captor.capture());
        UpdateUserRequest req = captor.getValue();
        assertThat(req.getUserId()).isEqualTo("u-1");
        assertThat(req.getFirstName()).isEqualTo("John");
        assertThat(req.getLastName()).isEqualTo("Doe");
        assertThat(req.getEmail()).isEqualTo("j@d.com");
        assertThat(req.getPhoneNumber()).isEqualTo("+1234");
        assertThat(req.getCountry()).isEqualTo("US");
    }

    @Test
    void updateUser_nullFirstName_notSetOnRequest() {
        client.updateUser("u-1", null, null, null, null, null, null, null, null, null, null, null);

        ArgumentCaptor<UpdateUserRequest> captor = ArgumentCaptor.forClass(UpdateUserRequest.class);
        verify(stub).updateUser(captor.capture());
        assertThat(captor.getValue().hasFirstName()).isFalse();
        assertThat(captor.getValue().hasLastName()).isFalse();
    }

    @Test
    void updateUser_returnsUserFromResponse() {
        UserInfo result = client.updateUser("u-1", null, null, null, null, null, null, null, null, null, null, null);
        assertThat(result).isEqualTo(defaultUser);
    }

    // ── searchUsers ───────────────────────────────────────────────────────────

    @Test
    void searchUsers_withQuery_setsQueryOnRequest() {
        client.searchUsers("john", 0, 10);

        ArgumentCaptor<SearchUsersRequest> captor = ArgumentCaptor.forClass(SearchUsersRequest.class);
        verify(stub).searchUsers(captor.capture());
        assertThat(captor.getValue().getQuery()).isEqualTo("john");
        assertThat(captor.getValue().getPage()).isEqualTo(0);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void searchUsers_nullQuery_doesNotSetQuery() {
        client.searchUsers(null, 1, 20);

        ArgumentCaptor<SearchUsersRequest> captor = ArgumentCaptor.forClass(SearchUsersRequest.class);
        verify(stub).searchUsers(captor.capture());
        assertThat(captor.getValue().hasQuery()).isFalse();
    }
}
