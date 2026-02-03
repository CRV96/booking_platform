package com.booking.platform.user_service.service;

import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;
import java.util.Map;

public interface UserService {

    String createUser(String email, String password, String firstName, String lastName,
                             Map<String, String> attributes);

    int getUserCount(String search);

    List<String> getUserRoles(String userId);
}
