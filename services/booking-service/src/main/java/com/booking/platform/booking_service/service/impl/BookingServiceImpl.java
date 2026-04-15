package com.booking.platform.booking_service.service.impl;

import com.booking.platform.booking_service.constants.EntityConst;
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
import com.booking.platform.booking_service.service.BookingService;
import com.booking.platform.common.grpc.event.EventInfo;
import com.booking.platform.common.logging.ApplicationLogger;
import com.booking.platform.common.logging.LogErrorCode;
import com.booking.platform.common.grpc.event.EventResponse;
import com.booking.platform.common.grpc.event.SeatCategoryInfo;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Core booking business logic.
 *
 * <p>The {@link #createBooking} flow uses a Redis distributed lock per
 * event + seat category to prevent race conditions when multiple users
 * attempt to book the same seats simultaneously.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final DistributedLockService lockService;
    private final EventServiceClient eventServiceClient;
    private final BookingEventPublisher bookingEventPublisher;
    private final BookingExpirationProperties expirationProperties;
    private final BookingProperties bookingProperties;

    @Override
    public BookingEntity createBooking(String userId, String eventId,
                                       String seatCategory, int quantity, String idempotencyKey) {

        // 1. Idempotency check BEFORE lock — avoids lock contention on retries
        Optional<BookingEntity> existing = bookingRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            ApplicationLogger.logMessage(log, Level.INFO, "Idempotent hit: returning existing booking for key='{}'", idempotencyKey);
            return existing.get();
        }

        // 2. Acquire distributed lock on event:category
        LockHandle lock = lockService.tryAcquire(eventId, seatCategory);
        if (lock == null) {
            throw new SeatLockException(eventId, seatCategory);
        }

        try {
            // 3. Double-check idempotency inside lock (race between step 1 and lock)
            existing = bookingRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                ApplicationLogger.logMessage(log, Level.INFO, "Idempotent hit (post-lock): key='{}'", idempotencyKey);
                return existing.get();
            }

            // 4. Validate event is published and seat category has enough seats
            var eventInfo = callEventService(() -> eventServiceClient.getEvent(eventId), eventId)
                    .getEvent();
            SeatCategoryInfo seatCat = validateEventAndFindSeat(eventId, eventInfo, seatCategory, quantity);

            // 5. Decrement seats atomically in event-service
            callEventService(() -> eventServiceClient.updateSeatAvailability(eventId, seatCategory, -quantity), eventId);

            // 6. Persist PENDING booking
            BigDecimal unitPrice = BigDecimal.valueOf(seatCat.getPrice());
            BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));

            BookingEntity booking = BookingEntity.builder()
                    .userId(userId)
                    .eventId(eventId)
                    .eventTitle(eventInfo.getTitle())
                    .status(BookingStatus.PENDING)
                    .seatCategory(seatCategory)
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .totalPrice(totalPrice)
                    .currency(seatCat.getCurrency())
                    .idempotencyKey(idempotencyKey)
                    .holdExpiresAt(Instant.now().plus(expirationProperties.getHoldDuration()))
                    .build();

            BookingEntity saved = bookingRepository.save(booking);
            ApplicationLogger.logMessage(log, Level.INFO, "Booking created: id='{}', event='{}', category='{}', qty={}, total={}",
                    saved.getId(), eventId, seatCategory, quantity, saved.getTotalPrice());

            // 8. Publish BookingCreatedEvent → triggers payment-service
            bookingEventPublisher.publishBookingCreated(saved);

            return saved;

        } finally {
            // 9. ALWAYS release lock
            lockService.release(lock);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BookingEntity getBooking(UUID bookingId, String userId) {
        return bookingRepository.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookingEntity> getUserBookings(String userId, int page,
                                               int pageSize, String statusFilter) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(pageSize, 1), bookingProperties.getPagination().getMaxPageSize()),
                Sort.by(Sort.Direction.DESC, EntityConst.Booking.CREATED_AT)
        );

        if (statusFilter != null && !statusFilter.isBlank()) {
            BookingStatus status;
            try {
                status = BookingStatus.valueOf(statusFilter);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid booking status filter: " + statusFilter);
            }
            return bookingRepository.findByUserIdAndStatus(userId, status, pageRequest);
        }
        return bookingRepository.findByUserId(userId, pageRequest);
    }

    @Override
    @Transactional
    public BookingEntity cancelBooking(UUID bookingId, String userId, String reason) {
        BookingEntity booking = bookingRepository.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));

        if (booking.getStatus() == BookingStatus.CANCELLED
                || booking.getStatus() == BookingStatus.REFUNDED
                || booking.getStatus() == BookingStatus.REFUND_PENDING) {
            throw new BookingAlreadyCancelledException(bookingId.toString());
        }

        return cancelAndRelease(booking, reason);
    }

    @Override
    @Transactional
    public BookingEntity confirmBooking(UUID bookingId) {
        BookingEntity booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));

        // Idempotent: already confirmed → return as-is
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            ApplicationLogger.logMessage(log, Level.INFO, "Booking '{}' is already CONFIRMED, returning as-is", bookingId);
            return booking;
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidBookingStateException(bookingId.toString(), booking.getStatus(), BookingStatus.PENDING);
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        BookingEntity saved = bookingRepository.save(booking);

        ApplicationLogger.logMessage(log, Level.INFO, "Booking confirmed: id='{}', event='{}', category='{}', qty={}, total={}",
                bookingId, booking.getEventId(), booking.getSeatCategory(),
                booking.getQuantity(), booking.getTotalPrice());

        // Publish BookingConfirmedEvent → triggers ticket generation + confirmation email
        bookingEventPublisher.publishBookingConfirmed(saved);

        return saved;
    }

    @Override
    @Transactional
    public void expireBooking(UUID bookingId) {
        findPendingBooking(bookingId, "expiration")
                .ifPresent(booking -> cancelAndRelease(booking, EntityConst.CancellationReason.HOLD_EXPIRED));
    }

    @Override
    @Transactional
    public void cancelBookingOnPaymentFailure(UUID bookingId, String reason) {
        findPendingBooking(bookingId, "payment failure cancellation")
                .ifPresent(booking -> cancelAndRelease(booking,
                        EntityConst.CancellationReason.PAYMENT_FAILED_PREFIX + reason));
    }

    // ─── Refund completion (P4-05) ───────────────────────────────────

    @Override
    @Transactional
    public void markBookingAsRefunded(UUID bookingId) {
        Optional<BookingEntity> optional = bookingRepository.findById(bookingId);
        if (optional.isEmpty()) {
            ApplicationLogger.logMessage(log, Level.DEBUG, "Booking '{}' no longer exists, skipping refund completion", bookingId);
            return;
        }

        BookingEntity booking = optional.get();

        // Idempotent: already refunded
        if (booking.getStatus() == BookingStatus.REFUNDED) {
            ApplicationLogger.logMessage(log, Level.INFO, "Booking '{}' is already REFUNDED, skipping", bookingId);
            return;
        }

        // Guard: only CANCELLED bookings should receive a refund completion
        if (booking.getStatus() != BookingStatus.CANCELLED) {
            ApplicationLogger.logMessage(log, Level.WARN, LogErrorCode.BOOKING_CANCELLATION_FAILED,
                    "Booking '{}' has unexpected status '{}' for refund completion, skipping",
                    bookingId, booking.getStatus());
            return;
        }

        booking.setStatus(BookingStatus.REFUNDED);
        bookingRepository.save(booking);

        ApplicationLogger.logMessage(log, Level.INFO, "Booking REFUNDED: id='{}', eventId='{}', category='{}', total={}",
                bookingId, booking.getEventId(), booking.getSeatCategory(), booking.getTotalPrice());
    }

    @Override
    public List<String> getAttendeeIdsForEvent(String eventId, BookingStatus status) {
        if(eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Event ID must not be null or blank");
        }

        if(status == null) {
            throw new IllegalArgumentException("Booking status must not be null");
        }

        final List<String> attendees = bookingRepository.findDistinctUserIdsByEventIdAndStatus(eventId, status);

        ApplicationLogger.logMessage(log, Level.DEBUG, "Found {} attendees for event '{}' and status {}", attendees.size(), eventId, status.name());

        return attendees;
    }

    // ─── Private helpers ─────────────────────────────────────────────

    private SeatCategoryInfo validateEventAndFindSeat(String eventId, EventInfo eventInfo,
                                                      String seatCategory, int quantity) {
        if (!EntityConst.EventStatus.PUBLISHED.equals(eventInfo.getStatus())) {
            throw new EventNotAvailableException(eventId,
                    "Event is not in PUBLISHED status: " + eventInfo.getStatus());
        }

        SeatCategoryInfo seatCat = eventInfo.getSeatCategoriesList().stream()
                .filter(sc -> sc.getName().equals(seatCategory))
                .findFirst()
                .orElseThrow(() -> new EventNotAvailableException(eventId,
                        "Seat category not found: " + seatCategory));

        if (seatCat.getAvailableSeats() < quantity) {
            throw new EventNotAvailableException(eventId,
                    "Insufficient seats: requested=" + quantity
                            + ", available=" + seatCat.getAvailableSeats());
        }

        return seatCat;
    }

    /**
     * Finds a PENDING booking by ID, returning empty if the booking
     * doesn't exist or is no longer PENDING (idempotent skip).
     */
    private Optional<BookingEntity> findPendingBooking(UUID bookingId, String operation) {
        Optional<BookingEntity> optional = bookingRepository.findById(bookingId);
        if (optional.isEmpty()) {
            ApplicationLogger.logMessage(log, Level.DEBUG, "Booking '{}' no longer exists, skipping {}", bookingId, operation);
            return Optional.empty();
        }

        BookingEntity booking = optional.get();
        if (booking.getStatus() != BookingStatus.PENDING) {
            ApplicationLogger.logMessage(log, Level.DEBUG, "Booking '{}' is no longer PENDING (status={}), skipping {}",
                    bookingId, booking.getStatus(), operation);
            return Optional.empty();
        }

        return Optional.of(booking);
    }

    /**
     * Cancels a booking, releases seats, and publishes the cancellation event.
     * Shared by user cancellation, hold expiration, and payment failure flows.
     */
    private BookingEntity cancelAndRelease(BookingEntity booking, String reason) {
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(reason);
        BookingEntity saved = bookingRepository.save(booking);

        releaseSeats(booking);

        ApplicationLogger.logMessage(log, Level.INFO, "Booking cancelled: id='{}', reason='{}'", booking.getId(), reason);

        bookingEventPublisher.publishBookingCancelled(saved);
        return saved;
    }

    private void releaseSeats(BookingEntity booking) {
        try {
            eventServiceClient.updateSeatAvailability(
                    booking.getEventId(), booking.getSeatCategory(), booking.getQuantity());
        } catch (Exception e) {
            ApplicationLogger.logMessage(log, Level.ERROR, LogErrorCode.SEAT_UPDATE_FAILED,
                    "Failed to release seats for booking '{}'", booking.getId(), e);
            // Seats will be recovered by eventual consistency / reconciliation
        }
    }

    /**
     * Wraps a gRPC call to event-service, mapping {@link StatusRuntimeException}
     * to domain exceptions.
     */
    private <T> T callEventService(Supplier<T> grpcCall, String eventId) {
        try {
            return grpcCall.get();
        } catch (StatusRuntimeException e) {
            ApplicationLogger.logMessage(log, Level.ERROR, LogErrorCode.GRPC_CALL_FAILED,
                    "Event-service gRPC call failed: {}", e.getStatus(), e);
            throw switch (e.getStatus().getCode()) {
                case NOT_FOUND -> new EventNotAvailableException(eventId, "Event not found");
                case FAILED_PRECONDITION -> new EventNotAvailableException(eventId,
                        e.getStatus().getDescription());
                default -> new EventNotAvailableException(eventId,
                        "Event service error: " + e.getStatus().getDescription());
            };
        }
    }
}
