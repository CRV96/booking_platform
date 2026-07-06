package com.booking.platform.graphql_gateway.service;

/**
 * Service for accessing authentication state in GraphQL resolvers.
 */
public interface AuthService {

    /**
     * Gets the authenticated user's ID from the JWT token.
     *
     * @return the user ID (sub claim)
     * @throws com.booking.platform.graphql_gateway.exception.GraphQLException
     *         with UNAUTHENTICATED if no valid authentication is present
     */
    String getAuthenticatedUserId();

    /**
     * Checks if the current request is authenticated.
     *
     * @return true if a valid JWT is present, false otherwise
     */
    boolean isAuthenticated();

    /**
     * Requires the authenticated user to have the specified role.
     * Roles are sourced from the {@code realm_access.roles} claim in the JWT,
     * mapped as Spring Security authorities with the "ROLE_" prefix.
     *
     * @param role the role name (e.g. "employee", "admin") — without "ROLE_" prefix
     * @throws com.booking.platform.graphql_gateway.exception.GraphQLException
     *         with UNAUTHENTICATED if no valid authentication is present
     * @throws com.booking.platform.graphql_gateway.exception.GraphQLException
     *         with FORBIDDEN if the user does not have the required role
     */
    void requireRole(String role);

    /**
     * Checks whether the authenticated user has the specified role.
     *
     * @param role the role name (e.g. "employee") — without "ROLE_" prefix
     * @return true if the user has the role, false otherwise
     */
    boolean hasRole(String role);
}
