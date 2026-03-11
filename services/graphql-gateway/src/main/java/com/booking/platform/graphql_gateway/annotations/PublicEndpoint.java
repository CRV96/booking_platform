package com.booking.platform.graphql_gateway.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a GraphQL resolver method as publicly accessible without authentication.
 *
 * By default, all resolver methods require a valid JWT token.
 * Methods annotated with {@code @PublicEndpoint} can be accessed without authentication.
 *
 * Usage:
 * <pre>
 * {@code
 * @PublicEndpoint
 * @MutationMapping
 * public AuthPayload login(@Argument("input") LoginInput input) {
 *     // This method can be called without a JWT token
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicEndpoint {
}
