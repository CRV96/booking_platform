package com.booking.platform.user_service.constants;

/**
 * Constants for Keycloak-specific values.
 */
public interface KeycloakConstants {

    // Groups
    String GROUP_CUSTOMERS = "customers";
    String GROUP_ADMINS = "admins";
    String GROUP_ORGANIZERS = "organizers";

    // Roles
    String ROLE_USER = "user";
    String ROLE_ADMIN = "admin";
    String ROLE_ORGANIZER = "organizer";

    // Grant Types
    String GRANT_TYPE_PASSWORD = "password";
    String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";
}
