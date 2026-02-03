package com.booking.platform.user_service.service;

import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;
import java.util.Map;

/**
 * Service for managing users in Keycloak via the Admin API.
 * Extends UserService with Keycloak-specific implementation marker.
 */
public interface KeycloakUserService extends UserService<UserRepresentation> {

    String createUser(String email, String password, String firstName, String lastName,
                      Map<String, String> attributes);

    UserRepresentation updateUser(String userId, String firstName, String lastName,
                                  String email, Map<String, String> attributes);

    List<UserRepresentation> searchUsers(String search, int page, int pageSize);

    List<String> getUserRoles(String userId);

    int getUserCount(String search);

}
