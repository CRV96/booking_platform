package com.booking.platform.user_service.constants;

/**
 * Constants for Keycloak-specific values.
 */
public final class KeycloakConstants {

    private KeycloakConstants() {}

    // Groups
    public static final String GROUP_CUSTOMERS = "customers";
    public static final String GROUP_EMPLOYEES = "employees";

    // Grant Types
    public static final String GRANT_TYPE_PASSWORD = "password";
    public static final String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";
}
