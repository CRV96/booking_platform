package com.booking.platform.graphql_gateway.constants;

/**
 * Role constants matching the Keycloak realm roles for the booking platform.
 * These correspond to realm_access.roles claims in the JWT.
 */
public final class Roles {

    private Roles() {}

    public static final String EMPLOYEE = "employee";
    public static final String CUSTOMER = "customer";
    public static final String ADMIN = "admin";
}
