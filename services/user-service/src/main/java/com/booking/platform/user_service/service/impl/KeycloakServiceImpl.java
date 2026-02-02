package com.booking.platform.user_service.service.impl;

import com.booking.platform.user_service.config.KeycloakProperties;
import com.booking.platform.user_service.exception.UserAlreadyExistsException;
import com.booking.platform.user_service.exception.UserNotFoundException;
import com.booking.platform.user_service.service.KeycloakUserService;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class KeycloakServiceImpl implements KeycloakUserService {
    private static final String CUSTOMERS_GROUP = "customers";

    private final Keycloak keycloak;
    private final KeycloakProperties keycloakProperties;

    @Override
    public String createUser(String email, String password, String firstName, String lastName, Map<String, String> attributes) {
        log.debug("Creating a new user with email: {}", email);

        UsersResource usersResource = getUsersResource();

        // Build user representation
        UserRepresentation user = new UserRepresentation();
        user.setUsername(email);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setEmailVerified(false);
        user.setGroups(List.of(CUSTOMERS_GROUP));

        // Set custom attributes
        if (attributes != null && !attributes.isEmpty()) {
            Map<String, List<String>> userAttributes = new HashMap<>();

            attributes.forEach((key, value) -> {
                if (value != null) {
                    userAttributes.put(key, List.of(value));
                }
            });

            user.setAttributes(userAttributes);
        }

        user.setCredentials(getPasswordCredentials(password));

        // Create user
        try (Response response = usersResource.create(user)) {
            if (response.getStatus() == 201)
            {
                String userId = extractUserIdFromResponse(response);
                log.info("User created successfully with ID: {}", userId);

                return userId;
            }
            else if (response.getStatus() == 409)
            {
                log.warn("User already exists with email: {}", email);

                throw new UserAlreadyExistsException("User with email " + email + " already exists");
            }
            else {
                String error = response.readEntity(String.class);
                log.error("Failed to create user. Status: {}, Error: {}", response.getStatus(), error);

                throw new RuntimeException("Failed to create user: " + error);
            }
        }
    }

    @Override
    public UserRepresentation updateUser(String userId, String firstName, String lastName,
                                         String email, Map<String, String> attributes) {
        log.info("Updating user: {}", userId);

        UserResource userResource = getUsersResource().get(userId);
        UserRepresentation user = userResource.toRepresentation();

        // Update basic fields if provided
        if (firstName != null) {
            user.setFirstName(firstName);
        }
        if (lastName != null) {
            user.setLastName(lastName);
        }
        if (email != null) {
            user.setEmail(email);
            user.setUsername(email); // Keep username in sync with email
        }

        // Update custom attributes
        if (attributes != null && !attributes.isEmpty()) {
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
        } else {
            return usersResource.list(firstResult, pageSize);
        }
    }

    @Override
    public int getUserCount(String search) {
        UsersResource usersResource = getUsersResource();

        if (search != null && !search.isBlank()) {
            return usersResource.search(search).size();
        } else {
            return usersResource.count();
        }
    }

    @Override
    public UserRepresentation getUserById(String userId) {
        log.debug("Fetching user by ID: {}", userId);

        try {
            UserResource userResource = getUsersResource().get(userId);
            return userResource.toRepresentation();
        } catch (Exception e) {
            log.warn("User not found with ID: {}", userId);
            throw new UserNotFoundException("User not found with ID: " + userId);
        }
    }

    @Override
    public UserRepresentation getUserByUsername(String username) {
        log.debug("Fetching user by username: {}", username);

        List<UserRepresentation> users = getUsersResource().searchByUsername(username, true);
        if (users.isEmpty()) {
            throw new UserNotFoundException("User not found with username: " + username);
        }
        return users.get(0);
    }

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
        UserResource userResource = getUsersResource().get(userId);
        return userResource.roles().realmLevel().listEffective()
                .stream()
                .map(role -> role.getName())
                .toList();
    }

    private List<CredentialRepresentation> getPasswordCredentials(final String password){
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);

        return List.of(credential);
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
