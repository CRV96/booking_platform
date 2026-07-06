package com.booking.platform.user_service.grpc.server;

import com.booking.platform.common.grpc.user.*;
import com.booking.platform.user_service.mapper.AttributeMapper;
import com.booking.platform.user_service.mapper.UserGrpcMapper;
import com.booking.platform.user_service.properties.ValidationProperties;
import com.booking.platform.user_service.service.KeycloakUserService;
import com.booking.platform.user_service.validation.UserValidator;
import com.booking.platform.common.logging.ApplicationLogger;
import com.booking.platform.common.logging.LogErrorCode;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.event.Level;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;
import java.util.Map;

/**
 * gRPC service implementation for user profile operations.
 * Handles user retrieval, updates, and search functionality.
 *
 * Exception handling is delegated to {@link com.booking.platform.common.grpc.interceptor.GrpcExceptionInterceptor}
 */
@GrpcService
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties(ValidationProperties.class)
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final KeycloakUserService keycloakUserService;
    private final UserGrpcMapper userGrpcMapper;
    private final AttributeMapper attributeMapper;
    private final UserValidator userValidator;
    private final ValidationProperties validationProperties;

    @Override
    public void getUser(GetUserRequest request, StreamObserver<UserResponse> responseObserver) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "gRPC GetUser request for ID: {}", request.getUserId());

        userValidator.validateUserId(request.getUserId());

        UserRepresentation user = keycloakUserService.getUserById(request.getUserId());
        sendUserResponse(user, responseObserver);
    }

    @Override
    public void getUserByUsername(GetUserByUsernameRequest request, StreamObserver<UserResponse> responseObserver) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "gRPC GetUserByUsername request: {}", request.getUsername());

        userValidator.validateUsername(request.getUsername());

        UserRepresentation user = keycloakUserService.getUserByUsername(request.getUsername());
        sendUserResponse(user, responseObserver);
    }

    @Override
    public void getUserByEmail(GetUserByEmailRequest request, StreamObserver<UserResponse> responseObserver) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "gRPC GetUserByEmail request: {}", request.getEmail());

        userValidator.validateEmail(request.getEmail());

        UserRepresentation user = keycloakUserService.getUserByEmail(request.getEmail());
        sendUserResponse(user, responseObserver);
    }

    @Override
    public void updateUser(UpdateUserRequest request, StreamObserver<UserResponse> responseObserver) {
        ApplicationLogger.logMessage(log, Level.INFO, "gRPC UpdateUser request for ID: {}", request.getUserId());

        userValidator.validateUpdateUserRequest(request);

        Map<String, String> attributes = attributeMapper.fromUpdateRequest(request);

        UserRepresentation user = keycloakUserService.updateUser(
                request.getUserId(),
                request.hasFirstName() ? request.getFirstName() : null,
                request.hasLastName() ? request.getLastName() : null,
                request.hasEmail() ? request.getEmail() : null,
                attributes
        );

        List<String> roles = keycloakUserService.getUserRoles(request.getUserId());

        UserResponse response = UserResponse.newBuilder()
                .setUser(userGrpcMapper.toUserInfo(user, roles))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
        ApplicationLogger.logMessage(log, Level.INFO, "User updated successfully: {}", request.getUserId());
    }

    @Override
    public void searchUsers(SearchUsersRequest request, StreamObserver<SearchUsersResponse> responseObserver) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "gRPC SearchUsers request: query='{}', page={}, size={}",
                request.hasQuery() ? request.getQuery() : "", request.getPage(), request.getPageSize());

        String query = request.hasQuery() ? request.getQuery() : null;
        int page = request.getPage();
        int pageSize = clampPageSize(request.getPageSize());

        List<UserRepresentation> users = keycloakUserService.searchUsers(query, page, pageSize);
        int totalCount = keycloakUserService.getUserCount(query);
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);

        // Fetch all roles in parallel (fixes N+1 problem)
        List<String> userIds = users.stream().map(UserRepresentation::getId).toList();
        Map<String, List<String>> usersRoles = keycloakUserService.getUsersRoles(userIds);

        SearchUsersResponse.Builder responseBuilder = SearchUsersResponse.newBuilder()
                .setTotalCount(totalCount)
                .setPage(page)
                .setPageSize(pageSize)
                .setTotalPages(totalPages);

        for (UserRepresentation user : users) {
            List<String> roles = usersRoles.getOrDefault(user.getId(), List.of());
            responseBuilder.addUsers(userGrpcMapper.toUserInfo(user, roles));
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getUserEmail(GetUserEmailRequest request, StreamObserver<UserEmailResponse> responseObserver) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "gRPC GetUserEmail request for ID: {}", request.getUserId());
        UserRepresentation userRepresentation = keycloakUserService.getUserById(request.getUserId());

        if(userRepresentation == null) {
            ApplicationLogger.logMessage(log, Level.ERROR, LogErrorCode.USER_NOT_FOUND, "User not found with ID: {}", request.getUserId());
            responseObserver.onError(new IllegalArgumentException("User not found with ID: " + request.getUserId()));
            return;
        }

        UserEmailResponse.Builder responseBuilder= UserEmailResponse.newBuilder()
                .setEmail(userRepresentation.getEmail());

        ApplicationLogger.logMessage(log, Level.DEBUG, "Fetched email for user ID {}: {}", request.getUserId(), userRepresentation.getEmail());
        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getUsersEmails(GetUsersEmailsRequest request, StreamObserver<GetUsersEmailsResponse> responseObserver) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "gRPC GetUsersEmails request for IDs: {}", request.getUserIdsList());
        List<UserRepresentation> users = keycloakUserService.getUsersByIds(request.getUserIdsList());

        List<String> emails = users.stream()
                .map(UserRepresentation::getEmail)
                .toList();

        ApplicationLogger.logMessage(log, Level.DEBUG, "Fetched emails for user IDs {}: {}", request.getUserIdsList(), emails);

        responseObserver.onNext(GetUsersEmailsResponse.newBuilder().addAllUserEmails(emails).build());
        responseObserver.onCompleted();
    }

    private int clampPageSize(int pageSize) {
        return Math.min(Math.max(pageSize, validationProperties.minPageSize()), validationProperties.maxPageSize());
    }

    private void sendUserResponse(UserRepresentation user, StreamObserver<UserResponse> responseObserver) {
        List<String> roles = keycloakUserService.getUserRoles(user.getId());

        UserResponse response = UserResponse.newBuilder()
                .setUser(userGrpcMapper.toUserInfo(user, roles))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
