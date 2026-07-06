package com.booking.platform.user_service.service;

import com.booking.platform.user_service.entity.UserAttributeEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Database read operations for users.
 * Reads directly from the database for better performance.
 * Write operations should go through Keycloak Admin API.
 */
public interface DatabaseUserService<USER, USER_ATTRIBUTE> extends UserLookupService<USER> {

    List<USER> getAllUsers();

    Page<USER> getAllUsers(Pageable pageable);

    Page<USER> searchUsers(String query, Pageable pageable);

    long getUserCount();

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    List<USER_ATTRIBUTE> getUserAttributes(String userId);

    List<USER_ATTRIBUTE> getUserAttribute(String userId, String attributeName);
}
