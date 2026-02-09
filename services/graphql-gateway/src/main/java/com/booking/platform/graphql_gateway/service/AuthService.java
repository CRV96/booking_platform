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
     * Requires authentication for the current request.
     * Call this at the start of protected resolver methods.
     *
     * @throws com.booking.platform.graphql_gateway.exception.GraphQLException
     *         with UNAUTHENTICATED if no valid authentication is present
     */
    void requireAuthentication();
}
