package com.booking.platform.user_service.service;

import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;
import java.util.Map;

/**
 * Service for managing users in Keycloak via the Admin API.
 * Extends UserService with Keycloak-specific implementation marker.
 */
public interface KeycloakUserService extends UserLookupService<UserRepresentation> {

    String createUser(String email, String password, String firstName, String lastName,
                      Map<String, String> attributes);

    UserRepresentation updateUser(String userId, String firstName, String lastName,
                                  String email, Map<String, String> attributes);

    List<UserRepresentation> searchUsers(String search, int page, int pageSize);

    List<String> getUserRoles(String userId);

    /**
     * Get roles for multiple users in parallel.
     * More efficient than calling getUserRoles() in a loop.
     *
     * @param userIds List of user IDs
     * @return Map of userId to their roles
     */
    Map<String, List<String>> getUsersRoles(List<String> userIds);

    int getUserCount(String search);

    /**
     * Triggers Keycloak to send a verification email to the user.
     * Keycloak generates the token, sends the email via its configured SMTP,
     * and marks emailVerified=true automatically when the link is clicked.
     *
     * @param userId the Keycloak user ID
     */
    void sendVerificationEmail(String userId);

    /**
     * Permanently deletes a user from Keycloak.
     *
     * @param userId the Keycloak user ID to delete
     */
    void deleteUser(String userId);

}
