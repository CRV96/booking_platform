package com.booking.platform.booking_service.grpc.service;

import com.booking.platform.booking_service.entity.FavoriteEntity;
import com.booking.platform.booking_service.service.LovelistService;
import com.booking.platform.booking_service.util.GrpcRequestUtils;
import com.booking.platform.booking_service.validation.LoveListValidation;
import com.booking.platform.common.grpc.booking.*;
import com.booking.platform.common.logging.ApplicationLogger;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.event.Level;

import java.time.Instant;
import java.util.List;

/**
 * gRPC endpoint for the per-user lovelist (favorites). Validates the request and
 * delegates to {@link LovelistService}.
 *
 * <p>The user ID is taken from the JWT via {@link GrpcRequestUtils#requireUserId()} —
 * never from the request. Every mutation responds with the full, freshly-read lovelist
 * so the caller ({@code graphql-gateway}) always has current state. Exceptions are mapped
 * to gRPC status codes by the shared {@code GrpcExceptionInterceptor}.</p>
 */
@GrpcService
@Slf4j
@RequiredArgsConstructor
public class LovelistGrpcService extends LoveListServiceGrpc.LoveListServiceImplBase {

    private final LovelistService lovelistService;
    private final LoveListValidation loveListValidation;

    @Override
    public void getLoveList(GetLoveListRequest request, StreamObserver<LoveListResponse> responseObserver) {
        String userId = GrpcRequestUtils.requireUserId();
        ApplicationLogger.logMessage(log, Level.DEBUG, "gRPC GetLoveList: user='{}'", userId);

        responseObserver.onNext(currentLoveList(userId));
        responseObserver.onCompleted();
    }

    @Override
    public void addToLoveList(AddToLoveListRequest request, StreamObserver<LoveListResponse> responseObserver) {
        String userId = GrpcRequestUtils.requireUserId();
        loveListValidation.validateEventId(request.getEventId());

        ApplicationLogger.logMessage(log, Level.DEBUG,
                "gRPC AddToLoveList: user='{}', event='{}'", userId, request.getEventId());

        lovelistService.addFavorite(userId, request.getEventId());

        responseObserver.onNext(currentLoveList(userId));
        responseObserver.onCompleted();
    }

    @Override
    public void removeFromLoveList(RemoveFromLoveListRequest request, StreamObserver<LoveListResponse> responseObserver) {
        String userId = GrpcRequestUtils.requireUserId();
        loveListValidation.validateEventId(request.getEventId());

        ApplicationLogger.logMessage(log, Level.DEBUG,
                "gRPC RemoveFromLoveList: user='{}', event='{}'", userId, request.getEventId());

        lovelistService.removeFavorite(userId, request.getEventId());

        responseObserver.onNext(currentLoveList(userId));
        responseObserver.onCompleted();
    }

    /** Reads the user's lovelist and maps it to the gRPC response. */
    private LoveListResponse currentLoveList(String userId) {
        List<LoveListItem> items = lovelistService.getFavorites(userId).stream()
                .map(this::toProto)
                .toList();
        return LoveListResponse.newBuilder().addAllItems(items).build();
    }

    private LoveListItem toProto(FavoriteEntity f) {
        Instant createdAt = f.getCreatedAt();
        return LoveListItem.newBuilder()
                .setEventId(f.getEventId())
                .setCreatedAt(createdAt != null ? createdAt.toString() : "")
                .build();
    }
}
