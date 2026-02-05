package com.booking.platform.graphql_gateway.graphql.resolver;

import com.booking.platform.common.grpc.user.SearchUsersResponse;
import com.booking.platform.common.grpc.user.UserInfo;
import com.booking.platform.graphql_gateway.grpc.client.UserOperationsClient;
import com.booking.platform.graphql_gateway.dto.user.UpdateProfileInput;
import com.booking.platform.graphql_gateway.dto.user.User;
import com.booking.platform.graphql_gateway.dto.user.UserConnection;
import com.booking.platform.graphql_gateway.exception.ErrorCode;
import com.booking.platform.graphql_gateway.exception.GraphQLException;
import com.booking.platform.graphql_gateway.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * GraphQL resolver for user queries and profile mutations.
 * Exceptions are handled by {@link com.booking.platform.graphql_gateway.exception.GraphQLExceptionHandler}
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class UserResolver {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final UserOperationsClient userOperationsClient;
    private final AuthService authService;

    @QueryMapping
    public User me() {
        String userId = authService.getAuthenticatedUserId();
        log.debug("GraphQL query: me() for user {}", userId);

        UserInfo userInfo = userOperationsClient.getUser(userId);
        return User.fromGrpc(userInfo);
    }

    @QueryMapping
    public User user(@Argument("id") String id) {
        log.debug("GraphQL query: user({})", id);

        UserInfo userInfo = userOperationsClient.getUser(id);

        return User.fromGrpc(userInfo);
    }

    @QueryMapping
    public UserConnection users(
            @Argument("query") String query,
            @Argument("page") Integer page,
            @Argument("pageSize") Integer pageSize) {
        log.debug("GraphQL query: users(query={}, page={}, pageSize={})", query, page, pageSize);

        int actualPage = page != null ? page : 0;
        int actualPageSize = pageSize != null ? pageSize : 20;

        SearchUsersResponse response = userOperationsClient.searchUsers(query, actualPage, actualPageSize);

        List<User> users = response.getUsersList().stream()
            .map(User::fromGrpc)
            .toList();

        return new UserConnection(
            users,
            response.getTotalCount(),
            response.getPage(),
            response.getPageSize(),
            response.getTotalPages()
        );
    }

    @MutationMapping
    public User updateProfile(@Argument("input") UpdateProfileInput input) {
        String userId = authService.getAuthenticatedUserId();
        log.info("GraphQL mutation: updateProfile for user {}", userId);

        UserInfo userInfo = userOperationsClient.updateUser(
                userId,
                input.firstName(),
                input.lastName(),
                input.email(),
                input.phoneNumber(),
                input.country(),
                input.preferredLanguage(),
                input.preferredCurrency(),
                input.timezone(),
                input.profilePictureUrl(),
                input.emailNotifications(),
                input.smsNotifications()
        );

        return User.fromGrpc(userInfo);
    }

}
