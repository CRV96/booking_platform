package com.booking.platform.user_service.service;

/**
 * Service for managing users in Keycloak via the Admin API.
 * Extends UserService with Keycloak-specific implementation marker.
 */
public interface KeycloakUserService extends UserService {
    // All methods are inherited from UserService
    // This interface exists for type clarity and future Keycloak-specific methods
}
