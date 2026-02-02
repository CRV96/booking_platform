package com.booking.platform.user_service.service;

import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;
import java.util.Map;

public interface UserService {

    String createUser(String email, String password, String firstName, String lastName,
                             Map<String, String> attributes);

    UserRepresentation getUserById(String userId);

    UserRepresentation getUserByUsername(String username);

    UserRepresentation getUserByEmail(String email);

    UserRepresentation updateUser(String userId, String firstName, String lastName,
                                  String email, Map<String, String> attributes);

    List<UserRepresentation> searchUsers(String search, int page, int pageSize);

    int getUserCount(String search);

    List<String> getUserRoles(String userId);
}
