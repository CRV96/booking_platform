package com.booking.platform.graphql_gateway.resolver;

import com.booking.platform.common.grpc.user.SearchUsersResponse;
import com.booking.platform.common.grpc.user.UserInfo;
import com.booking.platform.graphql_gateway.client.UserServiceClient;
import com.booking.platform.graphql_gateway.dto.UpdateProfileInput;
import com.booking.platform.graphql_gateway.dto.User;
import com.booking.platform.graphql_gateway.dto.UserConnection;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * GraphQL resolver for user queries and profile mutations.
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class UserResolver {

    private final UserServiceClient userServiceClient;

    // TODO: Implement authentication context to get current user ID
    // For now, these endpoints require the user ID to be passed explicitly

    @QueryMapping
    public User me() {
        // TODO: Get user ID from JWT token in security context
        // For now, return null - will implement with security
        log.warn("me() query called but authentication not yet implemented");
        return null;
    }

    @QueryMapping
    public User user(@Argument("id") String id) {
        log.debug("GraphQL query: user({})", id);

        try {
            UserInfo userInfo = userServiceClient.getUser(id);
            return User.fromGrpc(userInfo);

        } catch (StatusRuntimeException e) {
            log.error("Get user failed: {}", e.getStatus().getDescription());
            throw new AuthResolver.GraphQLException(
                mapGrpcCode(e),
                e.getStatus().getDescription()
            );
        }
    }

    @QueryMapping
    public UserConnection users(
            @Argument("query") String query,
            @Argument("page") Integer page,
            @Argument("pageSize") Integer pageSize) {
        log.debug("GraphQL query: users(query={}, page={}, pageSize={})", query, page, pageSize);

        int actualPage = page != null ? page : 0;
        int actualPageSize = pageSize != null ? pageSize : 20;

        try {
            SearchUsersResponse response = userServiceClient.searchUsers(query, actualPage, actualPageSize);

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

        } catch (StatusRuntimeException e) {
            log.error("Search users failed: {}", e.getStatus().getDescription());
            throw new AuthResolver.GraphQLException(
                mapGrpcCode(e),
                e.getStatus().getDescription()
            );
        }
    }

    @MutationMapping
    public User updateProfile(@Argument("input") UpdateProfileInput input) {
        // TODO: Get user ID from JWT token in security context
        // For now, this is a placeholder
        log.warn("updateProfile() called but authentication not yet implemented");
        throw new AuthResolver.GraphQLException(
            "NOT_IMPLEMENTED",
            "Authentication required - not yet implemented"
        );
    }

    private String mapGrpcCode(StatusRuntimeException e) {
        return switch (e.getStatus().getCode()) {
            case NOT_FOUND -> "USER_NOT_FOUND";
            case PERMISSION_DENIED -> "FORBIDDEN";
            case UNAUTHENTICATED -> "UNAUTHENTICATED";
            default -> "INTERNAL_ERROR";
        };
    }
}
