package com.booking.platform.notification_service.grpc.client.impl;

import com.booking.platform.common.grpc.user.GetUserEmailRequest;
import com.booking.platform.common.grpc.user.GetUsersEmailsRequest;
import com.booking.platform.common.grpc.user.UserEmailResponse;
import com.booking.platform.common.grpc.user.UserServiceGrpc;
import com.booking.platform.notification_service.grpc.client.UserServiceClient;
import com.booking.platform.common.logging.ApplicationLogger;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class UserServiceGrpcClient implements UserServiceClient {

    @GrpcClient("user-service")
    private UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    @Override
    public String getUserEmail(String userId) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Fetching email for user ID: {}", userId);
        return userServiceStub.getUserEmail(GetUserEmailRequest.newBuilder().setUserId(userId).build()).getEmail();
    }

    @Override
    public List<String> getUsersEmails(List<String> userIds) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Fetching emails for user IDs: {}", userIds);
        return userServiceStub.getUsersEmails(GetUsersEmailsRequest.newBuilder().addAllUserIds(userIds).build()).getUserEmailsList();
    }


}
