package com.booking.platform.booking_service.grpc;

import com.booking.platform.booking_service.entity.FavoriteEntity;
import com.booking.platform.booking_service.grpc.service.LovelistGrpcService;
import com.booking.platform.booking_service.service.LovelistService;
import com.booking.platform.booking_service.validation.LoveListValidation;
import com.booking.platform.common.grpc.booking.*;
import com.booking.platform.common.grpc.context.GrpcUserContext;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LovelistGrpcServiceTest {

    @Mock private LovelistService lovelistService;
    @Mock private LoveListValidation loveListValidation;
    @Mock private StreamObserver<LoveListResponse> observer;

    @InjectMocks private LovelistGrpcService grpcService;

    private static final String USER_ID = "user-1";
    private static final String EVENT_ID = "event-1";
    private static final Instant CREATED = Instant.parse("2025-06-10T12:00:00Z");

    private Context.CancellableContext grpcCtx;
    private Context previousContext;

    @BeforeEach
    void attachUserContext() {
        grpcCtx = Context.current().withValue(GrpcUserContext.USER_ID, USER_ID).withCancellation();
        previousContext = grpcCtx.attach();
        when(lovelistService.getFavorites(USER_ID)).thenReturn(List.of(favorite()));
    }

    @AfterEach
    void detachContext() {
        grpcCtx.detach(previousContext);
        grpcCtx.cancel(null);
    }

    private FavoriteEntity favorite() {
        return FavoriteEntity.builder()
                .id(UUID.randomUUID()).userId(USER_ID).eventId(EVENT_ID).createdAt(CREATED).build();
    }

    @Test
    void getLoveList_mapsEntitiesToResponse() {
        grpcService.getLoveList(GetLoveListRequest.getDefaultInstance(), observer);

        ArgumentCaptor<LoveListResponse> captor = ArgumentCaptor.forClass(LoveListResponse.class);
        verify(observer).onNext(captor.capture());
        assertThat(captor.getValue().getItemsCount()).isEqualTo(1);
        assertThat(captor.getValue().getItems(0).getEventId()).isEqualTo(EVENT_ID);
        assertThat(captor.getValue().getItems(0).getCreatedAt()).isEqualTo(CREATED.toString());
        verify(observer).onCompleted();
    }

    @Test
    void addToLoveList_valid_validatesAndDelegates() {
        grpcService.addToLoveList(AddToLoveListRequest.newBuilder().setEventId(EVENT_ID).build(), observer);

        verify(loveListValidation).validateEventId(EVENT_ID);
        verify(lovelistService).addFavorite(USER_ID, EVENT_ID);
        verify(observer).onCompleted();
    }

    @Test
    void addToLoveList_blankEventId_throws() {
        doThrow(new IllegalArgumentException("event_id is required"))
                .when(loveListValidation).validateEventId("");

        assertThatThrownBy(() -> grpcService.addToLoveList(
                AddToLoveListRequest.newBuilder().setEventId("").build(), observer))
                .isInstanceOf(IllegalArgumentException.class);
        verify(lovelistService, never()).addFavorite(any(), any());
    }

    @Test
    void removeFromLoveList_valid_delegates() {
        grpcService.removeFromLoveList(
                RemoveFromLoveListRequest.newBuilder().setEventId(EVENT_ID).build(), observer);

        verify(loveListValidation).validateEventId(EVENT_ID);
        verify(lovelistService).removeFavorite(USER_ID, EVENT_ID);
        verify(observer).onCompleted();
    }

    @Test
    void getLoveList_noUser_throws() {
        grpcCtx.detach(previousContext);
        grpcCtx.cancel(null);
        previousContext = Context.current().attach();

        assertThatThrownBy(() -> grpcService.getLoveList(GetLoveListRequest.getDefaultInstance(), observer))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
