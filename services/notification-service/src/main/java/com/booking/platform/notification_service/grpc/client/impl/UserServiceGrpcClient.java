package com.booking.platform.notification_service.grpc.client.impl;

import com.booking.platform.common.grpc.user.GetUserEmailRequest;
import com.booking.platform.common.grpc.user.UserEmailResponse;
import com.booking.platform.common.grpc.user.UserServiceGrpc;
import com.booking.platform.notification_service.grpc.client.UserServiceClient;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserServiceGrpcClient implements UserServiceClient {

    @GrpcClient("user-service")
    private UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    @Override
    public String getUserEmail(String userId) {
        log.debug("Fetching email for user ID: {}", userId);
        return userServiceStub.getUserEmail(GetUserEmailRequest.newBuilder().setUserId(userId).build()).getEmail();
    }
}
