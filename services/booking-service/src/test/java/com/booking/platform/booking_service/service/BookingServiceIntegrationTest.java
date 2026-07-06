package com.booking.platform.booking_service.service;

import com.booking.platform.booking_service.base.BaseIntegrationTest;
import com.booking.platform.booking_service.entity.BookingEntity;
import com.booking.platform.booking_service.entity.enums.BookingStatus;
import com.booking.platform.booking_service.exception.BookingAlreadyCancelledException;
import com.booking.platform.booking_service.exception.BookingNotFoundException;
import com.booking.platform.booking_service.exception.EventNotAvailableException;
import com.booking.platform.booking_service.grpc.client.EventServiceClient;
import com.booking.platform.booking_service.service.impl.BookingServiceImpl;
import com.booking.platform.common.grpc.event.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for {@link BookingServiceImpl}.
 * Uses real PostgreSQL and Redis via Testcontainers.
 * Event-service gRPC calls are mocked.
 */
class BookingServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @MockBean
    private EventServiceClient eventServiceClient;

    private static final String USER_ID = "user-123";
    private static final String EVENT_ID = "event-abc";
    private static final String SEAT_CATEGORY = "VIP";

    @BeforeEach
    void setupMocks() {
        // Default: return a PUBLISHED event with 100 VIP seats at $49.99
        when(eventServiceClient.getEvent(EVENT_ID))
                .thenReturn(buildEventResponse("PUBLISHED", 100, 49.99));

        when(eventServiceClient.updateSeatAvailability(anyString(), anyString(), anyInt()))
                .thenReturn(UpdateSeatAvailabilityResponse.newBuilder()
                        .setSuccess(true)
                        .setRemainingSeats(98)
                        .build());
    }

    // ─── Create Booking: Success ─────────────────────────────────────

    @Test
    void createBooking_success_returnsPendingBooking() {
        String idempotencyKey = UUID.randomUUID().toString();

        BookingEntity booking = bookingService.createBooking(
                USER_ID, EVENT_ID, SEAT_CATEGORY, 2, idempotencyKey);

        assertThat(booking.getId()).isNotNull();
        assertThat(booking.getUserId()).isEqualTo(USER_ID);
        assertThat(booking.getEventId()).isEqualTo(EVENT_ID);
        assertThat(booking.getEventTitle()).isEqualTo("Test Concert");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.getSeatCategory()).isEqualTo(SEAT_CATEGORY);
        assertThat(booking.getQuantity()).isEqualTo(2);
        assertThat(booking.getUnitPrice()).isEqualByComparingTo(new BigDecimal("49.99"));
        assertThat(booking.getTotalPrice()).isEqualByComparingTo(new BigDecimal("99.98"));
        assertThat(booking.getCurrency()).isEqualTo("USD");
        assertThat(booking.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(booking.getHoldExpiresAt()).isNotNull();

        // Verify event-service was called to decrement seats
        verify(eventServiceClient).updateSeatAvailability(EVENT_ID, SEAT_CATEGORY, -2);
    }

    // ─── Idempotency ────────────────────────────────────────────────

    @Test
    void createBooking_sameIdempotencyKey_returnsSameBooking() {
        String idempotencyKey = UUID.randomUUID().toString();

        BookingEntity first = bookingService.createBooking(
                USER_ID, EVENT_ID, SEAT_CATEGORY, 2, idempotencyKey);
        BookingEntity second = bookingService.createBooking(
                USER_ID, EVENT_ID, SEAT_CATEGORY, 2, idempotencyKey);

        assertThat(second.getId()).isEqualTo(first.getId());

        // Seat decrement should only happen once (first call)
        verify(eventServiceClient, times(1))
                .updateSeatAvailability(EVENT_ID, SEAT_CATEGORY, -2);
    }

    // ─── Event not found ────────────────────────────────────────────

    @Test
    void createBooking_eventNotFound_throwsException() {
        when(eventServiceClient.getEvent("missing-event"))
                .thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        assertThatThrownBy(() -> bookingService.createBooking(
                USER_ID, "missing-event", SEAT_CATEGORY, 1, UUID.randomUUID().toString()))
                .isInstanceOf(EventNotAvailableException.class)
                .hasMessageContaining("Event not found");
    }

    // ─── Insufficient seats ─────────────────────────────────────────

    @Test
    void createBooking_insufficientSeats_throwsException() {
        when(eventServiceClient.getEvent(EVENT_ID))
                .thenReturn(buildEventResponse("PUBLISHED", 0, 49.99));

        assertThatThrownBy(() -> bookingService.createBooking(
                USER_ID, EVENT_ID, SEAT_CATEGORY, 1, UUID.randomUUID().toString()))
                .isInstanceOf(EventNotAvailableException.class)
                .hasMessageContaining("Insufficient seats");
    }

    // ─── Event not PUBLISHED ────────────────────────────────────────

    @Test
    void createBooking_eventNotPublished_throwsException() {
        when(eventServiceClient.getEvent(EVENT_ID))
                .thenReturn(buildEventResponse("DRAFT", 100, 49.99));

        assertThatThrownBy(() -> bookingService.createBooking(
                USER_ID, EVENT_ID, SEAT_CATEGORY, 1, UUID.randomUUID().toString()))
                .isInstanceOf(EventNotAvailableException.class)
                .hasMessageContaining("not in PUBLISHED status");
    }

    // ─── Get Booking ────────────────────────────────────────────────

    @Test
    void getBooking_existingBooking_returnsIt() {
        String idempotencyKey = UUID.randomUUID().toString();
        BookingEntity created = bookingService.createBooking(
                USER_ID, EVENT_ID, SEAT_CATEGORY, 1, idempotencyKey);

        BookingEntity found = bookingService.getBooking(created.getId(), USER_ID);

        assertThat(found.getId()).isEqualTo(created.getId());
        assertThat(found.getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void getBooking_wrongUser_throwsNotFoundException() {
        String idempotencyKey = UUID.randomUUID().toString();
        BookingEntity created = bookingService.createBooking(
                USER_ID, EVENT_ID, SEAT_CATEGORY, 1, idempotencyKey);

        assertThatThrownBy(() -> bookingService.getBooking(created.getId(), "other-user"))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void getBooking_nonExistent_throwsNotFoundException() {
        assertThatThrownBy(() -> bookingService.getBooking(UUID.randomUUID(), USER_ID))
                .isInstanceOf(BookingNotFoundException.class);
    }

    // ─── Cancel Booking ─────────────────────────────────────────────

    @Test
    void cancelBooking_pendingBooking_succeeds() {
        String idempotencyKey = UUID.randomUUID().toString();
        BookingEntity created = bookingService.createBooking(
                USER_ID, EVENT_ID, SEAT_CATEGORY, 2, idempotencyKey);

        BookingEntity cancelled = bookingService.cancelBooking(
                created.getId(), USER_ID, "Changed my mind");

        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.getCancellationReason()).isEqualTo("Changed my mind");

        // Verify seats were released back (+2)
        verify(eventServiceClient).updateSeatAvailability(EVENT_ID, SEAT_CATEGORY, 2);
    }

    @Test
    void cancelBooking_alreadyCancelled_throwsException() {
        String idempotencyKey = UUID.randomUUID().toString();
        BookingEntity created = bookingService.createBooking(
                USER_ID, EVENT_ID, SEAT_CATEGORY, 1, idempotencyKey);

        bookingService.cancelBooking(created.getId(), USER_ID, "First cancel");

        assertThatThrownBy(() -> bookingService.cancelBooking(
                created.getId(), USER_ID, "Second cancel"))
                .isInstanceOf(BookingAlreadyCancelledException.class);
    }

    @Test
    void cancelBooking_seatReleaseFails_stillCancelsBooking() {
        // Simulate event-service being down when releasing seats
        doThrow(new StatusRuntimeException(Status.UNAVAILABLE))
                .when(eventServiceClient)
                .updateSeatAvailability(eq(EVENT_ID), eq(SEAT_CATEGORY), eq(2));

        String idempotencyKey = UUID.randomUUID().toString();
        BookingEntity created = bookingService.createBooking(
                USER_ID, EVENT_ID, SEAT_CATEGORY, 2, idempotencyKey);

        // Reset the mock for the cancel path (positive delta)
        doThrow(new StatusRuntimeException(Status.UNAVAILABLE))
                .when(eventServiceClient)
                .updateSeatAvailability(eq(EVENT_ID), eq(SEAT_CATEGORY), eq(2));

        // Cancel should still succeed (seat release is best-effort)
        BookingEntity cancelled = bookingService.cancelBooking(
                created.getId(), USER_ID, "Testing failure");

        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    // ─── Get User Bookings ──────────────────────────────────────────

    @Test
    void getUserBookings_returnsPagedResults() {
        // Create 3 bookings
        for (int i = 0; i < 3; i++) {
            bookingService.createBooking(
                    USER_ID, EVENT_ID, SEAT_CATEGORY, 1, UUID.randomUUID().toString());
        }

        var page = bookingService.getUserBookings(USER_ID, 0, 10, null);

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void getUserBookings_withStatusFilter_filtersCorrectly() {
        // Create 2 bookings, cancel 1
        BookingEntity b1 = bookingService.createBooking(
                USER_ID, EVENT_ID, SEAT_CATEGORY, 1, UUID.randomUUID().toString());
        bookingService.createBooking(
                USER_ID, EVENT_ID, SEAT_CATEGORY, 1, UUID.randomUUID().toString());

        bookingService.cancelBooking(b1.getId(), USER_ID, "cancel");

        var pendingPage = bookingService.getUserBookings(USER_ID, 0, 10, "PENDING");
        var cancelledPage = bookingService.getUserBookings(USER_ID, 0, 10, "CANCELLED");

        assertThat(pendingPage.getContent()).hasSize(1);
        assertThat(cancelledPage.getContent()).hasSize(1);
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private EventResponse buildEventResponse(String status, int availableSeats, double price) {
        return EventResponse.newBuilder()
                .setEvent(EventInfo.newBuilder()
                        .setId(EVENT_ID)
                        .setTitle("Test Concert")
                        .setStatus(status)
                        .addSeatCategories(SeatCategoryInfo.newBuilder()
                                .setName(SEAT_CATEGORY)
                                .setPrice(price)
                                .setCurrency("USD")
                                .setTotalSeats(100)
                                .setAvailableSeats(availableSeats)
                                .build())
                        .build())
                .build();
    }
}
