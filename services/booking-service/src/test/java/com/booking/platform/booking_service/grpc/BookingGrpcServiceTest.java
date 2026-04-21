package com.booking.platform.booking_service.grpc;

import com.booking.platform.booking_service.entity.BookingEntity;
import com.booking.platform.booking_service.entity.enums.BookingStatus;
import com.booking.platform.booking_service.mapper.BookingMapper;
import com.booking.platform.booking_service.properties.BookingProperties;
import com.booking.platform.booking_service.service.BookingService;
import com.booking.platform.common.grpc.booking.*;
import com.booking.platform.common.grpc.context.GrpcUserContext;
import com.booking.platform.booking_service.grpc.service.BookingGrpcService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingGrpcServiceTest {

    @Mock private BookingService bookingService;
    @Mock private BookingMapper bookingMapper;
    @Mock private BookingProperties bookingProperties;
    @Mock private StreamObserver<BookingResponse> bookingObserver;
    @Mock private StreamObserver<GetUserBookingsResponse> listObserver;
    @Mock private StreamObserver<GetBookingAttendeesResponse> attendeesObserver;

    @InjectMocks private BookingGrpcService grpcService;

    private static final String USER_ID = "u-1";
    private static final UUID BOOKING_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private Context.CancellableContext grpcCtx;
    private Context previousContext;

    @BeforeEach
    void attachUserContext() {
        grpcCtx = Context.current()
                .withValue(GrpcUserContext.USER_ID, USER_ID)
                .withCancellation();
        previousContext = grpcCtx.attach();

        BookingProperties.Pagination pagination = new BookingProperties.Pagination();
        when(bookingProperties.getPagination()).thenReturn(pagination);
        when(bookingMapper.toProto(any())).thenReturn(BookingInfo.getDefaultInstance());
        when(bookingMapper.toProtoList(any())).thenReturn(List.of());
    }

    @AfterEach
    void detachContext() {
        grpcCtx.detach(previousContext);
        grpcCtx.cancel(null);
    }

    private BookingEntity bookingEntity() {
        return BookingEntity.builder()
                .id(BOOKING_UUID)
                .userId(USER_ID)
                .eventId("ev-1")
                .eventTitle("Rock Fest")
                .status(BookingStatus.PENDING)
                .seatCategory("VIP")
                .quantity(2)
                .unitPrice(new BigDecimal("50.00"))
                .totalPrice(new BigDecimal("100.00"))
                .currency("USD")
                .idempotencyKey("idem-key")
                .holdExpiresAt(Instant.now())
                .build();
    }

    // ── createBooking ─────────────────────────────────────────────────────────

    @Test
    void createBooking_noUserId_throwsIllegalArgument() {
        grpcCtx.detach(previousContext);
        grpcCtx.cancel(null);
        previousContext = Context.current().attach(); // empty context — no userId

        assertThatThrownBy(() -> grpcService.createBooking(
                validCreateRequest().build(), bookingObserver))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user");
    }

    @Test
    void createBooking_blankEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> grpcService.createBooking(
                validCreateRequest().setEventId("").build(), bookingObserver))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("event_id");
    }

    @Test
    void createBooking_blankSeatCategory_throwsIllegalArgument() {
        assertThatThrownBy(() -> grpcService.createBooking(
                validCreateRequest().setSeatCategory("").build(), bookingObserver))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("seat_category");
    }

    @Test
    void createBooking_zeroQuantity_throwsIllegalArgument() {
        assertThatThrownBy(() -> grpcService.createBooking(
                validCreateRequest().setQuantity(0).build(), bookingObserver))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void createBooking_blankIdempotencyKey_throwsIllegalArgument() {
        assertThatThrownBy(() -> grpcService.createBooking(
                validCreateRequest().setIdempotencyKey("").build(), bookingObserver))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotency_key");
    }

    @Test
    void createBooking_valid_delegatesToService() {
        when(bookingService.createBooking(any(), any(), any(), anyInt(), any()))
                .thenReturn(bookingEntity());

        grpcService.createBooking(validCreateRequest().build(), bookingObserver);

        verify(bookingService).createBooking(USER_ID, "ev-1", "VIP", 2, "idem-key");
        verify(bookingObserver).onNext(any());
        verify(bookingObserver).onCompleted();
    }

    // ── getBooking ────────────────────────────────────────────────────────────

    @Test
    void getBooking_noUserId_throwsIllegalArgument() {
        grpcCtx.detach(previousContext);
        grpcCtx.cancel(null);
        previousContext = Context.current().attach();

        assertThatThrownBy(() -> grpcService.getBooking(
                GetBookingRequest.newBuilder().setBookingId(BOOKING_UUID.toString()).build(),
                bookingObserver))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getBooking_invalidUuid_throwsIllegalArgument() {
        assertThatThrownBy(() -> grpcService.getBooking(
                GetBookingRequest.newBuilder().setBookingId("not-a-uuid").build(),
                bookingObserver))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("booking_id");
    }

    @Test
    void getBooking_blankId_throwsIllegalArgument() {
        assertThatThrownBy(() -> grpcService.getBooking(
                GetBookingRequest.newBuilder().setBookingId("").build(), bookingObserver))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getBooking_valid_delegatesToService() {
        when(bookingService.getBooking(BOOKING_UUID, USER_ID)).thenReturn(bookingEntity());

        grpcService.getBooking(
                GetBookingRequest.newBuilder().setBookingId(BOOKING_UUID.toString()).build(),
                bookingObserver);

        verify(bookingService).getBooking(BOOKING_UUID, USER_ID);
        verify(bookingObserver).onCompleted();
    }

    // ── getUserBookings ───────────────────────────────────────────────────────

    @Test
    void getUserBookings_defaultPaging_usesProperties() {
        Page<BookingEntity> page = new PageImpl<>(List.of());
        when(bookingService.getUserBookings(eq(USER_ID), eq(0), eq(20), isNull()))
                .thenReturn(page);

        grpcService.getUserBookings(GetUserBookingsRequest.newBuilder().build(), listObserver);

        verify(bookingService).getUserBookings(USER_ID, 0, 20, null);
    }

    @Test
    void getUserBookings_explicitPaging_passedThrough() {
        Page<BookingEntity> page = new PageImpl<>(List.of());
        when(bookingService.getUserBookings(eq(USER_ID), eq(2), eq(15), isNull()))
                .thenReturn(page);

        grpcService.getUserBookings(
                GetUserBookingsRequest.newBuilder().setPage(2).setPageSize(15).build(),
                listObserver);

        verify(bookingService).getUserBookings(USER_ID, 2, 15, null);
    }

    @Test
    void getUserBookings_withStatusFilter_passesFilter() {
        Page<BookingEntity> page = new PageImpl<>(List.of());
        when(bookingService.getUserBookings(eq(USER_ID), anyInt(), anyInt(), eq("CONFIRMED")))
                .thenReturn(page);

        grpcService.getUserBookings(
                GetUserBookingsRequest.newBuilder().setStatusFilter("CONFIRMED").build(),
                listObserver);

        verify(bookingService).getUserBookings(USER_ID, 0, 20, "CONFIRMED");
    }

    @Test
    void getUserBookings_returnsResponseWithPagination() {
        BookingEntity entity = bookingEntity();
        Page<BookingEntity> page = new PageImpl<>(List.of(entity));
        when(bookingService.getUserBookings(any(), anyInt(), anyInt(), any())).thenReturn(page);
        when(bookingMapper.toProtoList(List.of(entity))).thenReturn(List.of(BookingInfo.getDefaultInstance()));

        grpcService.getUserBookings(GetUserBookingsRequest.newBuilder().build(), listObserver);

        ArgumentCaptor<GetUserBookingsResponse> captor = ArgumentCaptor.forClass(GetUserBookingsResponse.class);
        verify(listObserver).onNext(captor.capture());
        assertThat(captor.getValue().getBookingsCount()).isEqualTo(1);
        assertThat(captor.getValue().getPagination().getTotalCount()).isEqualTo(1);
        verify(listObserver).onCompleted();
    }

    // ── cancelBooking ─────────────────────────────────────────────────────────

    @Test
    void cancelBooking_blankReason_passesNull() {
        when(bookingService.cancelBooking(eq(BOOKING_UUID), eq(USER_ID), isNull()))
                .thenReturn(bookingEntity());

        grpcService.cancelBooking(
                CancelBookingRequest.newBuilder()
                        .setBookingId(BOOKING_UUID.toString())
                        .setReason("  ")
                        .build(),
                bookingObserver);

        verify(bookingService).cancelBooking(BOOKING_UUID, USER_ID, null);
    }

    @Test
    void cancelBooking_withReason_passesReason() {
        when(bookingService.cancelBooking(eq(BOOKING_UUID), eq(USER_ID), eq("changed mind")))
                .thenReturn(bookingEntity());

        grpcService.cancelBooking(
                CancelBookingRequest.newBuilder()
                        .setBookingId(BOOKING_UUID.toString())
                        .setReason("changed mind")
                        .build(),
                bookingObserver);

        verify(bookingService).cancelBooking(BOOKING_UUID, USER_ID, "changed mind");
        verify(bookingObserver).onCompleted();
    }

    // ── getBookingAttendees ───────────────────────────────────────────────────

    @Test
    void getBookingAttendees_returnsAttendeeList() {
        when(bookingService.getAttendeeIdsForEvent("ev-1", BookingStatus.CONFIRMED))
                .thenReturn(List.of("u-1", "u-2"));

        grpcService.getBookingAttendees(
                GetBookingAttendeesRequest.newBuilder()
                        .setEventId("ev-1")
                        .setEventStatus("CONFIRMED")
                        .build(),
                attendeesObserver);

        ArgumentCaptor<GetBookingAttendeesResponse> captor =
                ArgumentCaptor.forClass(GetBookingAttendeesResponse.class);
        verify(attendeesObserver).onNext(captor.capture());
        assertThat(captor.getValue().getAttendeesList()).containsExactly("u-1", "u-2");
        verify(attendeesObserver).onCompleted();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CreateBookingRequest.Builder validCreateRequest() {
        return CreateBookingRequest.newBuilder()
                .setEventId("ev-1")
                .setSeatCategory("VIP")
                .setQuantity(2)
                .setIdempotencyKey("idem-key");
    }
}
