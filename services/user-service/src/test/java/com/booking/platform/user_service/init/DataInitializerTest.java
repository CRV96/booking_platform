package com.booking.platform.user_service.init;

import com.booking.platform.user_service.properties.KeycloakProperties;
import com.booking.platform.user_service.service.KeycloakUserService;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.ApplicationArguments;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataInitializerTest {

    @Mock private KeycloakUserService keycloakUserService;
    @Mock private Keycloak keycloak;
    @Mock private KeycloakProperties keycloakProperties;
    @Mock private UsersResource usersResource;

    private DataInitializer initializer;
    private ApplicationArguments args;

    @BeforeEach
    void setUp() {
        initializer = new DataInitializer(keycloakUserService, keycloak, keycloakProperties);
        args = mock(ApplicationArguments.class);

        // Stub keycloakProperties.realm() for all tests
        when(keycloakProperties.realm()).thenReturn("booking-platform");

        // Stub the Keycloak admin client chain
        RealmResource realmResource = mock(RealmResource.class);
        when(keycloak.realm(anyString())).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);

        // Default: respond 201 to all employee creation calls
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(201);
        when(usersResource.create(any())).thenReturn(response);
    }

    // ── skip threshold ────────────────────────────────────────────────────────

    @Test
    void run_skipsSeeding_whenUserCountAboveThreshold() throws Exception {
        when(keycloakUserService.getUserCount(null)).thenReturn(5);

        initializer.run(args);

        verify(keycloakUserService, never()).createUser(anyString(), anyString(), anyString(), anyString(), any());
        verify(keycloak, never()).realm(anyString());
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void run_seeds60Users_whenBelowThreshold() throws Exception {
        when(keycloakUserService.getUserCount(null)).thenReturn(2);
        when(keycloakUserService.createUser(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn("generated-id");

        initializer.run(args);

        // 49 customers created via keycloakUserService.createUser
        verify(keycloakUserService, times(49))
                .createUser(anyString(), anyString(), anyString(), anyString(), any());

        // 10 employees created via usersResource.create
        verify(usersResource, times(10)).create(any(UserRepresentation.class));
    }

    // ── exception handling ────────────────────────────────────────────────────

    @Test
    void run_logsWarning_whenKeycloakUnavailable() {
        when(keycloakUserService.getUserCount(null))
                .thenThrow(new RuntimeException("connection refused"));

        assertThatCode(() -> initializer.run(args)).doesNotThrowAnyException();
    }

    @Test
    void createCustomer_skipsOnException() throws Exception {
        when(keycloakUserService.getUserCount(null)).thenReturn(2);

        // First call throws; subsequent calls succeed
        when(keycloakUserService.createUser(anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("conflict"))
                .thenReturn("generated-id");

        // run() should still complete normally, not propagate the exception
        assertThatCode(() -> initializer.run(args)).doesNotThrowAnyException();

        // At least some customers were still created after the first failure
        verify(keycloakUserService, atLeast(2))
                .createUser(anyString(), anyString(), anyString(), anyString(), any());
    }

    // ── employee creation ─────────────────────────────────────────────────────

    @Test
    void createEmployee_with201Response_doesNotThrow() throws Exception {
        when(keycloakUserService.getUserCount(null)).thenReturn(2);
        when(keycloakUserService.createUser(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn("generated-id");

        assertThatCode(() -> initializer.run(args)).doesNotThrowAnyException();

        verify(usersResource, times(10)).create(any(UserRepresentation.class));
    }

    @Test
    void createEmployee_withNon201Response_completesNormally() throws Exception {
        when(keycloakUserService.getUserCount(null)).thenReturn(2);
        when(keycloakUserService.createUser(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn("generated-id");

        Response badResponse = mock(Response.class);
        when(badResponse.getStatus()).thenReturn(400);
        when(usersResource.create(any())).thenReturn(badResponse);

        assertThatCode(() -> initializer.run(args)).doesNotThrowAnyException();
    }

    @Test
    void createEmployee_usesCorrectGroup() throws Exception {
        when(keycloakUserService.getUserCount(null)).thenReturn(2);
        when(keycloakUserService.createUser(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn("generated-id");

        initializer.run(args);

        ArgumentCaptor<UserRepresentation> captor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(usersResource, times(10)).create(captor.capture());

        // All captured employees should belong to the "employees" group
        captor.getAllValues().forEach(user ->
                assertThat(user.getGroups()).contains("employees")
        );
    }

    @Test
    void createEmployee_setsEmailVerifiedTrueAndEnabled() throws Exception {
        when(keycloakUserService.getUserCount(null)).thenReturn(2);
        when(keycloakUserService.createUser(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn("generated-id");

        initializer.run(args);

        ArgumentCaptor<UserRepresentation> captor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(usersResource, atLeastOnce()).create(captor.capture());

        UserRepresentation employee = captor.getValue();
        assertThat(employee.isEmailVerified()).isTrue();
        assertThat(employee.isEnabled()).isTrue();
    }
}
