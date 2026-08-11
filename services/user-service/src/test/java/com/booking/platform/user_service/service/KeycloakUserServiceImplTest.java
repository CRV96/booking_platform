package com.booking.platform.user_service.service;

import com.booking.platform.user_service.dto.UserCommandDTO;
import com.booking.platform.user_service.exception.InternalException;
import com.booking.platform.user_service.exception.user.UserAlreadyExistsException;
import com.booking.platform.user_service.exception.user.UserNotFoundException;
import com.booking.platform.user_service.properties.KeycloakProperties;
import com.booking.platform.user_service.service.impl.KeycloakUserServiceImpl;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.booking.platform.common.enums.Keycloak.CUSTOMERS_GROUP;
import static com.booking.platform.common.enums.Keycloak.EMPLOYEES_GROUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KeycloakUserServiceImplTest {

    @Mock private Keycloak keycloak;
    @Mock private RealmResource realmResource;
    @Mock private UsersResource usersResource;
    @Mock private UserResource userResource;
    @Mock private RoleMappingResource roleMappingResource;
    @Mock private RoleScopeResource roleScopeResource;
    @Mock private Response response;

    private final KeycloakProperties props =
            new KeycloakProperties("http://keycloak:8080", "booking-platform", "admin", "secret");

    private KeycloakUserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KeycloakUserServiceImpl(keycloak, props);
        ReflectionTestUtils.setField(service, "verificationEmailLifespanSeconds", 604800);

        when(keycloak.realm(anyString())).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get(anyString())).thenReturn(userResource);
    }

    // ── getUserById ───────────────────────────────────────────────────────────

    @Test
    void getUserById_found_returnsRepresentation() {
        UserRepresentation user = makeUser("user-1", "john@example.com");
        when(userResource.toRepresentation()).thenReturn(user);

        assertThat(service.getUserById("user-1")).isSameAs(user);
    }

    @Test
    void getUserById_keycloakNotFound_throwsUserNotFoundException() {
        when(userResource.toRepresentation()).thenThrow(new NotFoundException());

        assertThatThrownBy(() -> service.getUserById("missing"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("missing");
    }

    // ── getUserByUsername ─────────────────────────────────────────────────────

    @Test
    void getUserByUsername_found_returnsFirstResult() {
        UserRepresentation user = makeUser("user-1", "john@example.com");
        when(usersResource.searchByUsername("john", true)).thenReturn(List.of(user));

        assertThat(service.getUserByUsername("john")).isSameAs(user);
    }

    @Test
    void getUserByUsername_notFound_throwsUserNotFoundException() {
        when(usersResource.searchByUsername("missing", true)).thenReturn(List.of());

        assertThatThrownBy(() -> service.getUserByUsername("missing"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("missing");
    }

    // ── getUserByEmail ────────────────────────────────────────────────────────

    @Test
    void getUserByEmail_found_returnsFirstResult() {
        UserRepresentation user = makeUser("user-1", "john@example.com");
        when(usersResource.searchByEmail("john@example.com", true)).thenReturn(List.of(user));

        assertThat(service.getUserByEmail("john@example.com")).isSameAs(user);
    }

    @Test
    void getUserByEmail_notFound_throwsUserNotFoundException() {
        when(usersResource.searchByEmail("missing@x.com", true)).thenReturn(List.of());

        assertThatThrownBy(() -> service.getUserByEmail("missing@x.com"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("missing@x.com");
    }

    // ── createUser ────────────────────────────────────────────────────────────

    @Test
    void createUser_201Response_extractsUserIdFromLocationHeader() {
        when(response.getStatus()).thenReturn(201);
        when(response.getHeaderString(HttpHeaders.LOCATION))
                .thenReturn("http://keycloak/admin/realms/booking-platform/users/new-user-id");
        when(usersResource.create(any())).thenReturn(response);

        String userId = service.createUser(new UserCommandDTO(null, "a@b.com", "Pass1!", "John", "Doe", "customer", Map.of()));

        assertThat(userId).isEqualTo("new-user-id");
    }

    @Test
    void createUser_409Conflict_throwsUserAlreadyExistsException() {
        when(response.getStatus()).thenReturn(409);
        when(usersResource.create(any())).thenReturn(response);

        assertThatThrownBy(() -> service.createUser(new UserCommandDTO(null, "a@b.com", "Pass1!", "John", "Doe", "customer", Map.of())))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("a@b.com");
    }

    @Test
    void createUser_unexpectedStatus_throwsInternalException() {
        when(response.getStatus()).thenReturn(500);
        when(response.readEntity(String.class)).thenReturn("Internal Server Error");
        when(usersResource.create(any())).thenReturn(response);

        assertThatThrownBy(() -> service.createUser(new UserCommandDTO(null, "a@b.com", "Pass1!", "John", "Doe", "customer", Map.of())))
                .isInstanceOf(InternalException.class);
    }

    @Test
    void createUser_locationHeaderMissing_throwsInternalException() {
        when(response.getStatus()).thenReturn(201);
        when(response.getHeaderString(HttpHeaders.LOCATION)).thenReturn(null);
        when(usersResource.create(any())).thenReturn(response);

        assertThatThrownBy(() -> service.createUser(new UserCommandDTO(null, "a@b.com", "Pass1!", "John", "Doe", "customer", Map.of())))
                .isInstanceOf(InternalException.class)
                .hasMessageContaining("user ID");
    }

    // ── getUserRoles ──────────────────────────────────────────────────────────

    @Test
    void getUserRoles_returnsRoleNames() {
        stubRoles("user-1", List.of("USER", "ADMIN"));

        assertThat(service.getUserRoles("user-1")).containsExactlyInAnyOrder("USER", "ADMIN");
    }

    @Test
    void getUserRoles_noRoles_returnsEmptyList() {
        stubRoles("user-1", List.of());

        assertThat(service.getUserRoles("user-1")).isEmpty();
    }

    // ── getUsersRoles ─────────────────────────────────────────────────────────

    @Test
    void getUsersRoles_emptyInput_returnsEmptyMap() {
        assertThat(service.getUsersRoles(List.of())).isEmpty();
    }

    @Test
    void getUsersRoles_nullInput_returnsEmptyMap() {
        assertThat(service.getUsersRoles(null)).isEmpty();
    }

    @Test
    void getUsersRoles_multipleUsers_fetchesRolesForAll() {
        stubRoles("user-1", List.of("USER"));
        stubRoles("user-2", List.of("ADMIN"));

        Map<String, List<String>> result = service.getUsersRoles(List.of("user-1", "user-2"));

        assertThat(result).containsKey("user-1").containsKey("user-2");
        assertThat(result.get("user-1")).containsExactly("USER");
        assertThat(result.get("user-2")).containsExactly("ADMIN");
    }

    // ── deleteUser ────────────────────────────────────────────────────────────

    @Test
    void deleteUser_existing_callsRemove() {
        service.deleteUser("user-1");

        verify(userResource).remove();
    }

    @Test
    void deleteUser_notFound_doesNotThrow() {
        doThrow(new NotFoundException()).when(userResource).remove();

        org.assertj.core.api.Assertions.assertThatCode(() -> service.deleteUser("missing"))
                .doesNotThrowAnyException();
    }

    // ── getUsersByIds ─────────────────────────────────────────────────────────

    @Test
    void getUsersByIds_emptyInput_returnsEmptyList() {
        assertThat(service.getUsersByIds(List.of())).isEmpty();
    }

    @Test
    void getUsersByIds_nullInput_returnsEmptyList() {
        assertThat(service.getUsersByIds(null)).isEmpty();
    }

    @Test
    void getUsersByIds_mixedFoundAndNotFound_filtersNulls() {
        UserRepresentation user = makeUser("user-1", "a@b.com");
        when(usersResource.get("user-1")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(user);

        UserResource missing = mock(UserResource.class);
        when(usersResource.get("missing")).thenReturn(missing);
        when(missing.toRepresentation()).thenThrow(new NotFoundException());

        List<UserRepresentation> result = service.getUsersByIds(List.of("user-1", "missing"));

        assertThat(result).hasSize(1).contains(user);
    }

    // ── searchUsers ───────────────────────────────────────────────────────────

    @Test
    void searchUsers_withQuery_delegatesToSearch() {
        UserRepresentation user = makeUser("u1", "a@b.com");
        when(usersResource.search("john", 0, 10)).thenReturn(List.of(user));

        assertThat(service.searchUsers("john", 0, 10)).containsExactly(user);
    }

    @Test
    void searchUsers_blankQuery_delegatesToList() {
        UserRepresentation user = makeUser("u1", "a@b.com");
        when(usersResource.list(0, 10)).thenReturn(List.of(user));

        assertThat(service.searchUsers("", 0, 10)).containsExactly(user);
    }

    @Test
    void createUser_employeeRoleWithAttributes_buildsRepresentationAndSkipsNullAttribute() {
        when(response.getStatus()).thenReturn(201);
        when(response.getHeaderString(HttpHeaders.LOCATION))
                .thenReturn("http://keycloak/admin/realms/booking-platform/users/emp-1");
        ArgumentCaptor<UserRepresentation> captor = ArgumentCaptor.forClass(UserRepresentation.class);
        when(usersResource.create(captor.capture())).thenReturn(response);

        Map<String, String> attributes = new HashMap<>();
        attributes.put("phoneNumber", "555");
        attributes.put("nickname", null); // null value → skipped by buildUserRepresentation

        String id = service.createUser(new UserCommandDTO(
                null, "emp@x.com", "Pass1!", "Emp", "Loyee", "employee", attributes));

        assertThat(id).isEqualTo("emp-1");
        UserRepresentation built = captor.getValue();
        assertThat(built.getGroups()).containsExactly(EMPLOYEES_GROUP.getValue());
        assertThat(built.getAttributes()).containsEntry("phoneNumber", List.of("555"));
        assertThat(built.getAttributes()).doesNotContainKey("nickname");
    }

    @Test
    void createUser_customerRole_assignsCustomersGroup() {
        when(response.getStatus()).thenReturn(201);
        when(response.getHeaderString(HttpHeaders.LOCATION))
                .thenReturn("http://keycloak/admin/realms/booking-platform/users/cust-1");
        ArgumentCaptor<UserRepresentation> captor = ArgumentCaptor.forClass(UserRepresentation.class);
        when(usersResource.create(captor.capture())).thenReturn(response);

        service.createUser(new UserCommandDTO(null, "c@x.com", "Pass1!", "Cust", "Omer", "customer", Map.of()));

        assertThat(captor.getValue().getGroups()).containsExactly(CUSTOMERS_GROUP.getValue());
    }

    // ── updateUser ────────────────────────────────────────────────────────────

    @Test
    void updateUser_allFieldsProvided_updatesAndReturnsRepresentation() {
        UserRepresentation existing = makeUser("u-1", "old@x.com");
        when(userResource.toRepresentation()).thenReturn(existing);

        Map<String, String> attributes = Map.of("phoneNumber", "123");
        UserRepresentation result = service.updateUser(new UserCommandDTO(
                "u-1", "new@x.com", null, "New", "Name", null, attributes));

        verify(userResource).update(existing);
        assertThat(existing.getFirstName()).isEqualTo("New");
        assertThat(existing.getLastName()).isEqualTo("Name");
        assertThat(existing.getEmail()).isEqualTo("new@x.com");
        assertThat(existing.getUsername()).isEqualTo("new@x.com");
        assertThat(existing.getAttributes()).containsEntry("phoneNumber", List.of("123"));
        assertThat(result).isSameAs(existing);
    }

    @Test
    void updateUser_nullFields_leavesExistingValuesUnchanged() {
        UserRepresentation existing = makeUser("u-1", "old@x.com");
        existing.setFirstName("Old");
        existing.setLastName("Name");
        when(userResource.toRepresentation()).thenReturn(existing);

        service.updateUser(new UserCommandDTO("u-1", null, null, null, null, null, null));

        verify(userResource).update(existing);
        assertThat(existing.getFirstName()).isEqualTo("Old");
        assertThat(existing.getLastName()).isEqualTo("Name");
        assertThat(existing.getEmail()).isEqualTo("old@x.com");
    }

    @Test
    void updateUser_attributesMerge_updatesExistingAndRemovesNullValued() {
        UserRepresentation existing = makeUser("u-1", "a@b.com");
        Map<String, List<String>> current = new HashMap<>();
        current.put("phoneNumber", List.of("111"));
        current.put("country", List.of("US"));
        existing.setAttributes(current);
        when(userResource.toRepresentation()).thenReturn(existing);

        Map<String, String> update = new HashMap<>();
        update.put("phoneNumber", "222"); // overwrite
        update.put("country", null);       // remove

        service.updateUser(new UserCommandDTO("u-1", null, null, null, null, null, update));

        assertThat(existing.getAttributes()).containsEntry("phoneNumber", List.of("222"));
        assertThat(existing.getAttributes()).doesNotContainKey("country");
    }

    // ── getUserCount ──────────────────────────────────────────────────────────

    @Test
    void getUserCount_withSearch_returnsMatchCount() {
        when(usersResource.search("john"))
                .thenReturn(List.of(makeUser("u1", "a@b.com"), makeUser("u2", "b@c.com")));

        assertThat(service.getUserCount("john")).isEqualTo(2);
    }

    @Test
    void getUserCount_blankSearch_returnsTotalCount() {
        when(usersResource.count()).thenReturn(42);

        assertThat(service.getUserCount("")).isEqualTo(42);
    }

    // ── sendVerificationEmail ───────────────────────────────────────────────────

    @Test
    void sendVerificationEmail_triggersKeycloakActionsEmail() {
        service.sendVerificationEmail("u-1");

        verify(userResource).executeActionsEmail(List.of("VERIFY_EMAIL"), 604800);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void stubRoles(String userId, List<String> roleNames) {
        UserResource ur = mock(UserResource.class);
        when(usersResource.get(userId)).thenReturn(ur);

        RoleMappingResource rm = mock(RoleMappingResource.class);
        RoleScopeResource rs = mock(RoleScopeResource.class);
        when(ur.roles()).thenReturn(rm);
        when(rm.realmLevel()).thenReturn(rs);

        List<RoleRepresentation> roles = roleNames.stream()
                .map(name -> { RoleRepresentation r = new RoleRepresentation(); r.setName(name); return r; })
                .toList();
        when(rs.listEffective()).thenReturn(roles);
    }

    private UserRepresentation makeUser(String id, String email) {
        UserRepresentation user = new UserRepresentation();
        user.setId(id);
        user.setEmail(email);
        user.setUsername(email);
        return user;
    }
}
