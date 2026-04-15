package com.booking.platform.graphql_gateway.graphql.resolver;

import com.booking.platform.graphql_gateway.dto.auth.AuthPayload;
import com.booking.platform.graphql_gateway.dto.auth.LoginInput;
import com.booking.platform.graphql_gateway.dto.auth.LogoutPayload;
import com.booking.platform.graphql_gateway.dto.auth.RegisterInput;
import com.booking.platform.graphql_gateway.grpc.client.AuthClient;
import com.booking.platform.graphql_gateway.annotations.PublicEndpoint;
import com.booking.platform.common.logging.ApplicationLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

/**
 * GraphQL resolver for authentication mutations.
 * Exceptions are handled by {@link com.booking.platform.graphql_gateway.exception.GraphQLExceptionHandler}
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class AuthResolver {

    private final AuthClient authClient;

    @PublicEndpoint
    @MutationMapping
    public AuthPayload register(@Argument("input") RegisterInput input) {
        ApplicationLogger.logMessage(log, Level.INFO, "GraphQL mutation: register for {}", input.email());

        return AuthPayload.fromGrpc(authClient.register(
            input.email(),
            input.password(),
            input.firstName(),
            input.lastName(),
            input.phoneNumber(),
            input.country(),
            input.preferredLanguage()
        ));
    }

    @PublicEndpoint
    @MutationMapping
    public AuthPayload login(@Argument("input") LoginInput input) {
        ApplicationLogger.logMessage(log, Level.INFO, "GraphQL mutation: login for {}", input.username());

        return AuthPayload.fromGrpc(authClient.login(input.username(), input.password()));
    }

    @PublicEndpoint
    @MutationMapping
    public AuthPayload refreshToken(@Argument("refreshToken") String refreshToken) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "GraphQL mutation: refreshToken");

        return AuthPayload.fromGrpc(authClient.refreshToken(refreshToken));
    }

    @MutationMapping
    public LogoutPayload logout(@Argument("refreshToken") String refreshToken) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "GraphQL mutation: logout");

        return new LogoutPayload(authClient.logout(refreshToken));
    }
}
