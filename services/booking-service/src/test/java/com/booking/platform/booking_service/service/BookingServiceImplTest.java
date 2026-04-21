package com.booking.platform.booking_service.service;

import com.booking.platform.booking_service.entity.BookingEntity;
import com.booking.platform.booking_service.entity.enums.BookingStatus;
import com.booking.platform.booking_service.exception.*;
import com.booking.platform.booking_service.grpc.client.EventServiceClient;
import com.booking.platform.booking_service.lock.DistributedLockService;
import com.booking.platform.booking_service.lock.LockHandle;
import com.booking.platform.booking_service.messaging.publisher.BookingEventPublisher;
import com.booking.platform.booking_service.properties.BookingExpirationProperties;
import com.booking.platform.booking_service.properties.BookingProperties;
import com.booking.platform.booking_service.repository.BookingRepository;
import com.booking.platform.booking_service.service.impl.BookingServiceImpl;
import com.booking.platform.common.grpc.event.EventInfo;
import com.booking.platform.common.grpc.event.EventResponse;
import com.booking.platform.common.grpc.event.SeatCategoryInfo;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceImplTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private DistributedLockService lockService;
    @Mock private EventServiceClient eventServiceClient;
    @Mock private BookingEventPublisher bookingEventPublisher;
    @Mock private BookingExpirationProperties expirationProperties;
    @Mock private BookingProperties bookingProperties;

    @InjectMocks private BookingServiceImpl service;

    private static final String USER_ID = "u-1";
    private static final String EVENT_ID = "ev-1";
    private static final String SEAT_CATEGORY = "VIP";
    private static final String IDEM_KEY = "idem-abc";
    private static final LockHandle LOCK = new LockHandle("lock:seat:ev-1:VIP", "token-xyz");

    private BookingEntity pendingBooking(UUID id) {
        return BookingEntity.builder()
                .id(id)
                .userId(USER_ID)
                .eventId(EVENT_ID)
                .eventTitle("Rock Fest")
                .status(BookingStatus.PENDING)
                .seatCategory(SEAT_CATEGORY)
                .quantity(2)
                .unitPrice(new BigDecimal("50.00"))
                .totalPrice(new BigDecimal("100.00"))
                .currency("USD")
                .idempotencyKey(IDEM_KEY)
                .holdExpiresAt(Instant.now().plus(Duration.ofMinutes(10)))
                .build();
    }

    private EventResponse publishedEventResponse(int availableSeats) {
        return EventResponse.newBuilder()
                .setEvent(EventInfo.newBuilder()
                        .setId(EVENT_ID)
                        .setTitle("Rock Fest")
                        .setStatus("PUBLISHED")
                        .addSeatCategories(SeatCategoryInfo.newBuilder()
                                .setName(SEAT_CATEGORY)
                                .setPrice(50.0)
                                .setCurrency("USD")
                                .setAvailableSeats(availableSeats)
                                .setTotalSeats(100)
                                .build())
                        .build())
                .build();
    }

    @BeforeEach
    void setUp() {
        when(expirationProperties.getHoldDuration()).thenReturn(Duration.ofMinutes(10));
        BookingProperties.Pagination pagination = new BookingProperties.Pagination();
        when(bookingProperties.getPagination()).thenReturn(pagination);
    }

    // ── createBooking ─────────────────────────────────────────────────────────

    @Test
    void createBooking_idempotencyHitBeforeLock_returnsExisting() {
        BookingEntity existing = pendingBooking(UUID.randomUUID());
        when(bookingRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.of(existing));

        BookingEntity result = service.createBooking(USER_ID, EVENT_ID, SEAT_CATEGORY, 2, IDEM_KEY);

        assertThat(result).isSameAs(existing);
        verifyNoInteractions(lockService);
    }

    @Test
    void createBooking_lockNotAcquired_throwsSeatLockException() {
        when(bookingRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
        when(lockService.tryAcquire(EVENT_ID, SEAT_CATEGORY)).thenReturn(null);

        assertThatThrownBy(() -> service.createBooking(USER_ID, EVENT_ID, SEAT_CATEGORY, 2, IDEM_KEY))
                .isInstanceOf(SeatLockException.class);
    }

    @Test
    void createBooking_idempotencyHitInsideLock_returnsExisting() {
        BookingEntity existing = pendingBooking(UUID.randomUUID());
        when(bookingRepository.findByIdempotencyKey(IDEM_KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(lockService.tryAcquire(EVENT_ID, SEAT_CATEGORY)).thenReturn(LOCK);

        BookingEntity result = service.createBooking(USER_ID, EVENT_ID, SEAT_CATEGORY, 2, IDEM_KEY);

        assertThat(result).isSameAs(existing);
        verify(lockService).release(LOCK);
        verifyNoInteractions(eventServiceClient);
    }

    @Test
    void createBooking_eventNotPublished_throwsEventNotAvailable() {
        when(bookingRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
        when(lockService.tryAcquire(EVENT_ID, SEAT_CATEGORY)).thenReturn(LOCK);
        when(eventServiceClient.getEvent(EVENT_ID)).thenReturn(EventResponse.newBuilder()
                .setEvent(EventInfo.newBuilder().setId(EVENT_ID).setStatus("DRAFT").build())
                .build());

        assertThatThrownBy(() -> service.createBooking(USER_ID, EVENT_ID, SEAT_CATEGORY, 2, IDEM_KEY))
                .isInstanceOf(EventNotAvailableException.class)
                .hasMessageContaining(EVENT_ID);

        verify(lockService).release(LOCK);
    }

    @Test
    void createBooking_seatCategoryNotFound_throwsEventNotAvailable() {
        when(bookingRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
        when(lockService.tryAcquire(EVENT_ID, SEAT_CATEGORY)).thenReturn(LOCK);
        when(eventServiceClient.getEvent(EVENT_ID)).thenReturn(EventResponse.newBuilder()
                .setEvent(EventInfo.newBuilder().setId(EVENT_ID).setStatus("PUBLISHED").build())
                .build());

        assertThatThrownBy(() -> service.createBooking(USER_ID, EVENT_ID, SEAT_CATEGORY, 2, IDEM_KEY))
                .isInstanceOf(EventNotAvailableException.class)
                .hasMessageContaining("Seat category not found");

        verify(lockService).release(LOCK);
    }

    @Test
    void createBooking_insufficientSeats_throwsEventNotAvailable() {
        when(bookingRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
        when(lockService.tryAcquire(EVENT_ID, SEAT_CATEGORY)).thenReturn(LOCK);
        when(eventServiceClient.getEvent(EVENT_ID)).thenReturn(publishedEventResponse(1)); // only 1 available

        assertThatThrownBy(() -> service.createBooking(USER_ID, EVENT_ID, SEAT_CATEGORY, 2, IDEM_KEY))
                .isInstanceOf(EventNotAvailableException.class)
                .hasMessageContaining("Insufficient seats");

        verify(lockService).release(LOCK);
    }

    @Test
    void createBooking_grpcNotFound_throwsEventNotAvailable() {
        when(bookingRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
        when(lockService.tryAcquire(EVENT_ID, SEAT_CATEGORY)).thenReturn(LOCK);
        when(eventServiceClient.getEvent(EVENT_ID))
                .thenThrow(Status.NOT_FOUND.withDescription("not found").asRuntimeException());

        assertThatThrownBy(() -> service.createBooking(USER_ID, EVENT_ID, SEAT_CATEGORY, 2, IDEM_KEY))
                .isInstanceOf(EventNotAvailableException.class)
                .hasMessageContaining("Event not found");

        verify(lockService).release(LOCK);
    }

    @Test
    void createBooking_grpcFailedPrecondition_throwsEventNotAvailable() {
        when(bookingRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
        when(lockService.tryAcquire(EVENT_ID, SEAT_CATEGORY)).thenReturn(LOCK);
        when(eventServiceClient.getEvent(EVENT_ID))
                .thenThrow(Status.FAILED_PRECONDITION.withDescription("precondition").asRuntimeException());

        assertThatThrownBy(() -> service.createBooking(USER_ID, EVENT_ID, SEAT_CATEGORY, 2, IDEM_KEY))
                .isInstanceOf(EventNotAvailableException.class)
                .hasMessageContaining("precondition");

        verify(lockService).release(LOCK);
    }

    @Test
    void createBooking_grpcOtherError_throwsEventNotAvailable() {
        when(bookingRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
        when(lockService.tryAcquire(EVENT_ID, SEAT_CATEGORY)).thenReturn(LOCK);
        when(eventServiceClient.getEvent(EVENT_ID))
                .thenThrow(Status.INTERNAL.withDescription("oops").asRuntimeException());

        assertThatThrownBy(() -> service.createBooking(USER_ID, EVENT_ID, SEAT_CATEGORY, 2, IDEM_KEY))
                .isInstanceOf(EventNotAvailableException.class)
                .hasMessageContaining("Event service error");

        verify(lockService).release(LOCK);
    }

    @Test
    void createBooking_success_savesAndPublishes() {
        UUID id = UUID.randomUUID();
        BookingEntity saved = pendingBooking(id);

        when(bookingRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
        when(lockService.tryAcquire(EVENT_ID, SEAT_CATEGORY)).thenReturn(LOCK);
        when(eventServiceClient.getEvent(EVENT_ID)).thenReturn(publishedEventResponse(10));
        when(eventServiceClient.updateSeatAvailability(eq(EVENT_ID), eq(SEAT_CATEGORY), eq(-2)))
                .thenReturn(null);
        when(bookingRepository.save(any())).thenReturn(saved);

        BookingEntity result = service.createBooking(USER_ID, EVENT_ID, SEAT_CATEGORY, 2, IDEM_KEY);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.PENDING);
        verify(bookingRepository).save(argThat(b ->
                b.getStatus() == BookingStatus.PENDING
                        && b.getTotalPrice().compareTo(new BigDecimal("100.00")) == 0));
        verify(bookingEventPublisher).publishBookingCreated(saved);
        verify(lockService).release(LOCK);
    }

    @Test
    void createBooking_lockAlwaysReleasedOnException() {
        when(bookingRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
        when(lockService.tryAcquire(EVENT_ID, SEAT_CATEGORY)).thenReturn(LOCK);
        when(eventServiceClient.getEvent(EVENT_ID)).thenThrow(new RuntimeException("unexpected"));

        assertThatThrownBy(() -> service.createBooking(USER_ID, EVENT_ID, SEAT_CATEGORY, 2, IDEM_KEY));

        verify(lockService).release(LOCK);
    }

    // ── getBooking ────────────────────────────────────────────────────────────

    @Test
    void getBooking_found_returnsEntity() {
        UUID id = UUID.randomUUID();
        BookingEntity booking = pendingBooking(id);
        when(bookingRepository.findByIdAndUserId(id, USER_ID)).thenReturn(Optional.of(booking));

        assertThat(service.getBooking(id, USER_ID)).isSameAs(booking);
    }

    @Test
    void getBooking_notFound_throwsBookingNotFoundException() {
        UUID id = UUID.randomUUID();
        when(bookingRepository.findByIdAndUserId(id, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBooking(id, USER_ID))
                .isInstanceOf(BookingNotFoundException.class);
    }

    // ── getUserBookings ───────────────────────────────────────────────────────

    @Test
    void getUserBookings_noStatusFilter_queriesAll() {
        when(bookingRepository.findByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getUserBookings(USER_ID, 0, 10, null);

        verify(bookingRepository).findByUserId(eq(USER_ID), any(Pageable.class));
        verify(bookingRepository, never()).findByUserIdAndStatus(any(), any(), any());
    }

    @Test
    void getUserBookings_withStatusFilter_queriesFiltered() {
        when(bookingRepository.findByUserIdAndStatus(eq(USER_ID), eq(BookingStatus.CONFIRMED), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getUserBookings(USER_ID, 0, 10, "CONFIRMED");

        verify(bookingRepository).findByUserIdAndStatus(eq(USER_ID), eq(BookingStatus.CONFIRMED), any(Pageable.class));
    }

    @Test
    void getUserBookings_invalidStatusFilter_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getUserBookings(USER_ID, 0, 10, "BOGUS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid booking status filter");
    }

    @Test
    void getUserBookings_capsPageSizeAtMax() {
        when(bookingRepository.findByUserId(eq(USER_ID), argThat(p -> p.getPageSize() == 100)))
                .thenReturn(Page.empty());

        service.getUserBookings(USER_ID, 0, 9999, null);

        verify(bookingRepository).findByUserId(eq(USER_ID), argThat(p -> p.getPageSize() == 100));
    }

    @Test
    void getUserBookings_negativePage_usesZero() {
        when(bookingRepository.findByUserId(eq(USER_ID), argThat(p -> p.getPageNumber() == 0)))
                .thenReturn(Page.empty());

        service.getUserBookings(USER_ID, -5, 10, null);

        verify(bookingRepository).findByUserId(eq(USER_ID), argThat(p -> p.getPageNumber() == 0));
    }

    // ── cancelBooking ─────────────────────────────────────────────────────────

    @Test
    void cancelBooking_notFound_throwsBookingNotFoundException() {
        UUID id = UUID.randomUUID();
        when(bookingRepository.findByIdAndUserId(id, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelBooking(id, USER_ID, "reason"))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void cancelBooking_alreadyCancelled_throwsAlreadyCancelled() {
        UUID id = UUID.randomUUID();
        BookingEntity booking = pendingBooking(id);
        booking.setStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findByIdAndUserId(id, USER_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.cancelBooking(id, USER_ID, "reason"))
                .isInstanceOf(BookingAlreadyCancelledException.class);
    }

    @Test
    void cancelBooking_alreadyRefunded_throwsAlreadyCancelled() {
        UUID id = UUID.randomUUID();
        BookingEntity booking = pendingBooking(id);
        booking.setStatus(BookingStatus.REFUNDED);
        when(bookingRepository.findByIdAndUserId(id, USER_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.cancelBooking(id, USER_ID, "reason"))
                .isInstanceOf(BookingAlreadyCancelledException.class);
    }

    @Test
    void cancelBooking_pending_cancelledAndSeatsReleased() {
        UUID id = UUID.randomUUID();
        BookingEntity booking = pendingBooking(id);
        when(bookingRepository.findByIdAndUserId(id, USER_ID)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BookingEntity result = service.cancelBooking(id, USER_ID, "changed mind");

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(result.getCancellationReason()).isEqualTo("changed mind");
        verify(eventServiceClient).updateSeatAvailability(EVENT_ID, SEAT_CATEGORY, 2);
        verify(bookingEventPublisher).publishBookingCancelled(result);
    }

    @Test
    void cancelBooking_seatReleaseFails_stillCancelsBooking() {
        UUID id = UUID.randomUUID();
        BookingEntity booking = pendingBooking(id);
        when(bookingRepository.findByIdAndUserId(id, USER_ID)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Redis down"))
                .when(eventServiceClient).updateSeatAvailability(anyString(), anyString(), anyInt());

        // Should not propagate — seat release is best-effort
        BookingEntity result = service.cancelBooking(id, USER_ID, null);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(bookingEventPublisher).publishBookingCancelled(result);
    }

    // ── confirmBooking ────────────────────────────────────────────────────────

    @Test
    void confirmBooking_notFound_throwsBookingNotFoundException() {
        UUID id = UUID.randomUUID();
        when(bookingRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmBooking(id))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void confirmBooking_alreadyConfirmed_idempotentReturn() {
        UUID id = UUID.randomUUID();
        BookingEntity booking = pendingBooking(id);
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(id)).thenReturn(Optional.of(booking));

        BookingEntity result = service.confirmBooking(id);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void confirmBooking_notPending_throwsInvalidState() {
        UUID id = UUID.randomUUID();
        BookingEntity booking = pendingBooking(id);
        booking.setStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findById(id)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.confirmBooking(id))
                .isInstanceOf(InvalidBookingStateException.class)
                .hasMessageContaining("CANCELLED");
    }

    @Test
    void confirmBooking_pending_setsConfirmedAndPublishes() {
        UUID id = UUID.randomUUID();
        BookingEntity booking = pendingBooking(id);
        when(bookingRepository.findById(id)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BookingEntity result = service.confirmBooking(id);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookingEventPublisher).publishBookingConfirmed(result);
    }

    // ── expireBooking ─────────────────────────────────────────────────────────

    @Test
    void expireBooking_notFound_skipsGracefully() {
        UUID id = UUID.randomUUID();
        when(bookingRepository.findById(id)).thenReturn(Optional.empty());

        service.expireBooking(id); // no exception

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void expireBooking_notPending_skipsGracefully() {
        UUID id = UUID.randomUUID();
        BookingEntity booking = pendingBooking(id);
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(id)).thenReturn(Optional.of(booking));

        service.expireBooking(id);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void expireBooking_pending_cancelledWithHoldExpiredReason() {
        UUID id = UUID.randomUUID();
        BookingEntity booking = pendingBooking(id);
        when(bookingRepository.findById(id)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.expireBooking(id);

        verify(bookingRepository).save(argThat(b ->
                b.getStatus() == BookingStatus.CANCELLED
                        && "HOLD_EXPIRED".equals(b.getCancellationReason())));
        verify(bookingEventPublisher).publishBookingCancelled(any());
    }

    // ── cancelBookingOnPaymentFailure ─────────────────────────────────────────

    @Test
    void cancelOnPaymentFailure_notPending_skipsGracefully() {
        UUID id = UUID.randomUUID();
        BookingEntity booking = pendingBooking(id);
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(id)).thenReturn(Optional.of(booking));

        service.cancelBookingOnPaymentFailure(id, "card declined");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancelOnPaymentFailure_pending_cancelledWithPrefixedReason() {
        UUID id = UUID.randomUUID();
        BookingEntity booking = pendingBooking(id);
        when(bookingRepository.findById(id)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.cancelBookingOnPaymentFailure(id, "card declined");

        verify(bookingRepository).save(argThat(b ->
                b.getStatus() == BookingStatus.CANCELLED
                        && b.getCancellationReason().contains("card declined")));
    }

    // ── markBookingAsRefunded ─────────────────────────────────────────────────

    @Test
    void markAsRefunded_notFound_skipsGracefully() {
        UUID id = UUID.randomUUID();
        when(bookingRepository.findById(id)).thenReturn(Optional.empty());

        service.markBookingAsRefunded(id); // no exception

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void markAsRefunded_alreadyRefunded_skipsGracefully() {
        UUID id = UUID.randomUUID();
        BookingEntity booking = pendingBooking(id);
        booking.setStatus(BookingStatus.REFUNDED);
        when(bookingRepository.findById(id)).thenReturn(Optional.of(booking));

        service.markBookingAsRefunded(id);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void markAsRefunded_notCancelled_skipsGracefully() {
        UUID id = UUID.randomUUID();
        BookingEntity booking = pendingBooking(id);
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(id)).thenReturn(Optional.of(booking));

        service.markBookingAsRefunded(id);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void markAsRefunded_cancelled_setsRefundedStatus() {
        UUID id = UUID.randomUUID();
        BookingEntity booking = pendingBooking(id);
        booking.setStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findById(id)).thenReturn(Optional.of(booking));

        service.markBookingAsRefunded(id);

        verify(bookingRepository).save(argThat(b -> b.getStatus() == BookingStatus.REFUNDED));
    }

    // ── getAttendeeIdsForEvent ────────────────────────────────────────────────

    @Test
    void getAttendees_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getAttendeeIdsForEvent(null, BookingStatus.CONFIRMED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getAttendees_blankEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getAttendeeIdsForEvent("  ", BookingStatus.CONFIRMED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getAttendees_nullStatus_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getAttendeeIdsForEvent(EVENT_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getAttendees_success_delegatesToRepository() {
        when(bookingRepository.findDistinctUserIdsByEventIdAndStatus(EVENT_ID, BookingStatus.CONFIRMED))
                .thenReturn(List.of("u-1", "u-2"));

        List<String> result = service.getAttendeeIdsForEvent(EVENT_ID, BookingStatus.CONFIRMED);

        assertThat(result).containsExactly("u-1", "u-2");
    }
}
