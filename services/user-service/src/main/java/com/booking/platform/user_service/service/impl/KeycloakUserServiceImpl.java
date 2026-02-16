package com.booking.platform.user_service.service.impl;

import com.booking.platform.user_service.config.CacheConfig;
import com.booking.platform.user_service.properties.KeycloakProperties;
import com.booking.platform.user_service.constants.KeycloakConstants;
import com.booking.platform.user_service.exception.user.UserAlreadyExistsException;
import com.booking.platform.user_service.exception.user.UserNotFoundException;
import com.booking.platform.user_service.service.KeycloakUserService;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keycloak Admin API implementation for user management.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KeycloakUserServiceImpl implements KeycloakUserService {

    private final Keycloak keycloak;
    private final KeycloakProperties keycloakProperties;

    @Override
    public String createUser(String email, String password, String firstName, String lastName,
                             Map<String, String> attributes) {
        log.debug("Creating a new user with email: {}", email);

        UsersResource usersResource = getUsersResource();

        UserRepresentation user = buildUserRepresentation(email, firstName, lastName, attributes);
        user.setCredentials(createPasswordCredential(password));

        try (Response response = usersResource.create(user)) {
            return handleCreateUserResponse(response, email);
        }
    }

    @Caching(evict = {
            @CacheEvict(value = CacheConfig.CACHE_USER_BY_ID, key = "#a0"),
            @CacheEvict(value = CacheConfig.CACHE_USER_BY_EMAIL, key = "#result.email"),
            @CacheEvict(value = CacheConfig.CACHE_USER_BY_USERNAME, key = "#result.username")
    })
    @Override
    public UserRepresentation updateUser(String userId, String firstName, String lastName,
                                         String email, Map<String, String> attributes) {
        log.info("Updating user: {}", userId);

        UserResource userResource = getUsersResource().get(userId);
        UserRepresentation user = userResource.toRepresentation();

        updateBasicFields(user, firstName, lastName, email);
        updateAttributes(user, attributes);

        userResource.update(user);
        log.info("User updated successfully: {}", userId);

        return userResource.toRepresentation();
    }

    @Override
    public List<UserRepresentation> searchUsers(String search, int page, int pageSize) {
        log.debug("Searching users with query: '{}', page: {}, size: {}", search, page, pageSize);

        UsersResource usersResource = getUsersResource();
        int firstResult = page * pageSize;

        if (search != null && !search.isBlank()) {
            return usersResource.search(search, firstResult, pageSize);
        }
        return usersResource.list(firstResult, pageSize);
    }

    @Override
    public int getUserCount(String search) {
        UsersResource usersResource = getUsersResource();

        if (search != null && !search.isBlank()) {
            return usersResource.search(search).size();
        }
        return usersResource.count();
    }

    @Cacheable(value = CacheConfig.CACHE_USER_BY_ID, key = "#a0")
    @Override
    public UserRepresentation getUserById(String userId) {
        log.debug("Fetching user by ID: {}", userId);

        try {
            return getUsersResource().get(userId).toRepresentation();
        } catch (Exception e) {
            log.warn("User not found with ID: {}", userId);
            throw new UserNotFoundException("User not found with ID: " + userId);
        }
    }

    @Cacheable(value = CacheConfig.CACHE_USER_BY_USERNAME, key = "#a0")
    @Override
    public UserRepresentation getUserByUsername(String username) {
        log.debug("Fetching user by username: {}", username);

        List<UserRepresentation> users = getUsersResource().searchByUsername(username, true);
        if (users.isEmpty()) {
            throw new UserNotFoundException("User not found with username: " + username);
        }
        return users.get(0);
    }

    @Cacheable(value = CacheConfig.CACHE_USER_BY_EMAIL, key = "#a0")
    @Override
    public UserRepresentation getUserByEmail(String email) {
        log.debug("Fetching user by email: {}", email);

        List<UserRepresentation> users = getUsersResource().searchByEmail(email, true);
        if (users.isEmpty()) {
            throw new UserNotFoundException("User not found with email: " + email);
        }
        return users.get(0);
    }

    @Override
    public List<String> getUserRoles(String userId) {
        return fetchUserRoles(userId);
    }

    @Override
    public Map<String, List<String>> getUsersRoles(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        log.debug("Fetching roles for {} users in parallel", userIds.size());

        Map<String, List<String>> result = new ConcurrentHashMap<>();

        // Fetch all roles in parallel
        List<CompletableFuture<Void>> futures = userIds.stream()
                .map(userId -> CompletableFuture.runAsync(() ->
                        result.put(userId, fetchUserRoles(userId))))
                .toList();

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return result;
    }

    // ==================== Private Helper Methods ====================

    private List<String> fetchUserRoles(String userId) {
        return getUsersResource().get(userId)
                .roles()
                .realmLevel()
                .listEffective()
                .stream()
                .map(RoleRepresentation::getName)
                .toList();
    }

    private UserRepresentation buildUserRepresentation(String email, String firstName, String lastName,
                                                       Map<String, String> attributes) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(email);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setEmailVerified(false);
        user.setGroups(List.of(KeycloakConstants.GROUP_CUSTOMERS));

        if (attributes != null && !attributes.isEmpty()) {
            Map<String, List<String>> userAttributes = new HashMap<>();
            attributes.forEach((key, value) -> {
                if (value != null) {
                    userAttributes.put(key, List.of(value));
                }
            });
            user.setAttributes(userAttributes);
        }

        return user;
    }

    private List<CredentialRepresentation> createPasswordCredential(String password) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        return List.of(credential);
    }

    private String handleCreateUserResponse(Response response, String email) {
        int status = response.getStatus();

        if (status == 201) {
            String userId = extractUserIdFromResponse(response);
            log.info("User created successfully with ID: {}", userId);
            return userId;
        }

        if (status == 409) {
            log.warn("User already exists with email: {}", email);
            throw new UserAlreadyExistsException("User with email " + email + " already exists");
        }

        String error = response.readEntity(String.class);
        log.error("Failed to create user. Status: {}, Error: {}", status, error);
        throw new RuntimeException("Failed to create user: " + error);
    }

    private void updateBasicFields(UserRepresentation user, String firstName, String lastName, String email) {
        if (firstName != null) {
            user.setFirstName(firstName);
        }
        if (lastName != null) {
            user.setLastName(lastName);
        }
        if (email != null) {
            user.setEmail(email);
            user.setUsername(email);
        }
    }

    private void updateAttributes(UserRepresentation user, Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }

        Map<String, List<String>> existingAttributes = user.getAttributes();
        if (existingAttributes == null) {
            existingAttributes = new HashMap<>();
        }

        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (entry.getValue() != null) {
                existingAttributes.put(entry.getKey(), List.of(entry.getValue()));
            } else {
                existingAttributes.remove(entry.getKey());
            }
        }
        user.setAttributes(existingAttributes);
    }

    private UsersResource getUsersResource() {
        RealmResource realmResource = keycloak.realm(keycloakProperties.realm());
        return realmResource.users();
    }

    private String extractUserIdFromResponse(Response response) {
        String location = response.getHeaderString("Location");
        if (location != null) {
            return location.substring(location.lastIndexOf('/') + 1);
        }
        throw new RuntimeException("Could not extract user ID from response");
    }
}
