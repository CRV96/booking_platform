package com.booking.platform.graphql_gateway.graphql.resolver;

import com.booking.platform.common.grpc.user.SearchUsersResponse;
import com.booking.platform.common.grpc.user.UserInfo;
import com.booking.platform.graphql_gateway.grpc.client.UserOperationsClient;
import com.booking.platform.graphql_gateway.dto.user.UpdateProfileInput;
import com.booking.platform.graphql_gateway.dto.user.User;
import com.booking.platform.graphql_gateway.dto.user.UserConnection;
import com.booking.platform.graphql_gateway.exception.ErrorCode;
import com.booking.platform.graphql_gateway.exception.GraphQLException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * GraphQL resolver for user queries and profile mutations.
 * Exceptions are handled by {@link com.booking.platform.graphql_gateway.exception.GraphQLExceptionHandler}
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class UserResolver {

    private final UserOperationsClient userOperationsClient;

    @QueryMapping
    public User me() {
        // TODO: Get user ID from JWT token in security context
        log.warn("me() query called but authentication not yet implemented");
        return null;
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
        // TODO: Get user ID from JWT token in security context
        log.warn("updateProfile() called but authentication not yet implemented");
        throw new GraphQLException(ErrorCode.NOT_IMPLEMENTED);
    }
}
