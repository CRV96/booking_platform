package com.booking.platform.user_service.service;

import java.util.List;

/**
 * Base interface for user lookup operations.
 * Extended by {@link DatabaseUserService} (read-only DB queries) and {@link KeycloakUserService} (Admin API).
 */
public interface UserLookupService<USER> {
    USER getUserById(String userId);
    USER getUserByUsername(String username);
    USER getUserByEmail(String email);
    List<USER> getUsersByIds(List<String> userIds);
}
