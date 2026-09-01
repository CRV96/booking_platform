package com.booking.platform.graphql_gateway.grpc.client.impl;

import com.booking.platform.common.grpc.booking.*;
import com.booking.platform.common.logging.ApplicationLogger;
import com.booking.platform.graphql_gateway.constants.BookingServiceConst;
import com.booking.platform.graphql_gateway.grpc.client.LovelistClient;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.event.Level;
import org.springframework.stereotype.Service;

/**
 * gRPC client for the lovelist endpoints on booking-service.
 * JWT is forwarded automatically by {@code JwtForwardingClientInterceptor}.
 */
@Service
@Slf4j
public class LovelistServiceClientImpl implements LovelistClient {

    @GrpcClient(BookingServiceConst.GRPC_CLIENT)
    private LoveListServiceGrpc.LoveListServiceBlockingStub lovelistStub;

    @Override
    public LoveListResponse getLoveList() {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling booking-service: GetLoveList");
        return lovelistStub.getLoveList(GetLoveListRequest.getDefaultInstance());
    }

    @Override
    public LoveListResponse addFavorite(String eventId) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling booking-service: AddToLoveList event='{}'", eventId);
        return lovelistStub.addToLoveList(AddToLoveListRequest.newBuilder()
                .setEventId(eventId)
                .build());
    }

    @Override
    public LoveListResponse removeFavorite(String eventId) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling booking-service: RemoveFromLoveList event='{}'", eventId);
        return lovelistStub.removeFromLoveList(RemoveFromLoveListRequest.newBuilder()
                .setEventId(eventId)
                .build());
    }
}
