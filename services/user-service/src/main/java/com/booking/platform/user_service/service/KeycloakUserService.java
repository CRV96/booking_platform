package com.booking.platform.user_service.service;

import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;
import java.util.Map;

/**
 * Service for managing users in Keycloak via the Admin API.
 * Extends UserService with Keycloak-specific implementation marker.
 */
public interface KeycloakUserService extends UserService {
    // All methods are inherited from UserService
    // This interface exists for type clarity and future Keycloak-specific methods
    UserRepresentation getUserById(String userId);

    UserRepresentation getUserByUsername(String username);

    UserRepresentation getUserByEmail(String email);

    UserRepresentation updateUser(String userId, String firstName, String lastName,
                                  String email, Map<String, String> attributes);

    List<UserRepresentation> searchUsers(String search, int page, int pageSize);

}
