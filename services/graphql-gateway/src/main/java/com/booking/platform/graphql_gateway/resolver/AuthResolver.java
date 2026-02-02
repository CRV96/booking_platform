package com.booking.platform.graphql_gateway.resolver;

import com.booking.platform.common.grpc.user.AuthResponse;
import com.booking.platform.graphql_gateway.client.UserServiceClient;
import com.booking.platform.graphql_gateway.dto.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;

/**
 * GraphQL resolver for authentication mutations.
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class AuthResolver {

    private final UserServiceClient userServiceClient;

    @MutationMapping
    public AuthPayload register(@Argument("input") RegisterInput input) {
        log.info("GraphQL mutation: register for {}", input.email());

        try {
            AuthResponse response = userServiceClient.register(
                input.email(),
                input.password(),
                input.firstName(),
                input.lastName(),
                input.phoneNumber(),
                input.country(),
                input.preferredLanguage()
            );

            return mapToAuthPayload(response);

        } catch (StatusRuntimeException e) {
            log.error("Registration failed: {}", e.getStatus().getDescription());
            throw mapGrpcException(e);
        }
    }

    @MutationMapping
    public AuthPayload login(@Argument("input") LoginInput input) {
        log.info("GraphQL mutation: login for {}", input.username());

        try {
            AuthResponse response = userServiceClient.login(
                input.username(),
                input.password()
            );

            return mapToAuthPayload(response);

        } catch (StatusRuntimeException e) {
            log.error("Login failed: {}", e.getStatus().getDescription());
            throw mapGrpcException(e);
        }
    }

    @MutationMapping
    public AuthPayload refreshToken(@Argument("refreshToken") String refreshToken) {
        log.debug("GraphQL mutation: refreshToken");

        try {
            AuthResponse response = userServiceClient.refreshToken(refreshToken);
            return mapToAuthPayload(response);

        } catch (StatusRuntimeException e) {
            log.error("Token refresh failed: {}", e.getStatus().getDescription());
            throw mapGrpcException(e);
        }
    }

    @MutationMapping
    public LogoutPayload logout(@Argument("refreshToken") String refreshToken) {
        log.debug("GraphQL mutation: logout");

        boolean success = userServiceClient.logout(refreshToken);
        return new LogoutPayload(success);
    }

    /**
     * Maps gRPC AuthResponse to GraphQL AuthPayload.
     */
    private AuthPayload mapToAuthPayload(AuthResponse response) {
        User user = null;
        if (response.hasUser()) {
            user = User.fromGrpc(response.getUser());
        }

        return new AuthPayload(
            response.getAccessToken(),
            response.getRefreshToken(),
            response.getExpiresIn(),
            response.getRefreshExpiresIn(),
            response.getTokenType(),
            user
        );
    }

    /**
     * Maps gRPC StatusRuntimeException to a GraphQL-friendly exception.
     */
    private RuntimeException mapGrpcException(StatusRuntimeException e) {
        Status.Code code = e.getStatus().getCode();
        String description = e.getStatus().getDescription();

        return switch (code) {
            case ALREADY_EXISTS -> new GraphQLException("USER_ALREADY_EXISTS", description);
            case UNAUTHENTICATED -> new GraphQLException("INVALID_CREDENTIALS", description);
            case NOT_FOUND -> new GraphQLException("USER_NOT_FOUND", description);
            case INVALID_ARGUMENT -> new GraphQLException("INVALID_INPUT", description);
            default -> new GraphQLException("INTERNAL_ERROR", "An unexpected error occurred");
        };
    }

    /**
     * Custom exception that carries an error code for GraphQL responses.
     */
    public static class GraphQLException extends RuntimeException {
        private final String code;

        public GraphQLException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
