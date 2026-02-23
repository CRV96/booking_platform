package com.booking.platform.booking_service.service;

import com.booking.platform.booking_service.entity.BookingEntity;
import com.booking.platform.booking_service.entity.BookingStatus;
import com.booking.platform.booking_service.exception.*;
import com.booking.platform.booking_service.grpc.client.EventServiceClient;
import com.booking.platform.booking_service.lock.DistributedLockService;
import com.booking.platform.booking_service.lock.LockHandle;
import com.booking.platform.booking_service.repository.BookingRepository;
import com.booking.platform.common.grpc.event.EventResponse;
import com.booking.platform.common.grpc.event.SeatCategoryInfo;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

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

    private static final Duration HOLD_DURATION = Duration.ofMinutes(10);

    private final BookingRepository bookingRepository;
    private final DistributedLockService lockService;
    private final EventServiceClient eventServiceClient;

    @Override
    public BookingEntity createBooking(String userId, String eventId,
                                       String seatCategory, int quantity, String idempotencyKey) {

        // 1. Idempotency check BEFORE lock — avoids lock contention on retries
        Optional<BookingEntity> existing = bookingRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent hit: returning existing booking for key='{}'", idempotencyKey);
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
                log.info("Idempotent hit (post-lock): key='{}'", idempotencyKey);
                return existing.get();
            }

            // 4. Call event-service to validate event + get pricing
            EventResponse eventResponse;
            try {
                eventResponse = eventServiceClient.getEvent(eventId);
            } catch (StatusRuntimeException e) {
                throw mapGrpcException(eventId, e);
            }

            var eventInfo = eventResponse.getEvent();

            if (!"PUBLISHED".equals(eventInfo.getStatus())) {
                throw new EventNotAvailableException(eventId,
                        "Event is not in PUBLISHED status: " + eventInfo.getStatus());
            }

            // 5. Find the seat category and validate availability
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

            // 6. Decrement seats atomically in event-service
            try {
                eventServiceClient.updateSeatAvailability(eventId, seatCategory, -quantity);
            } catch (StatusRuntimeException e) {
                throw mapGrpcException(eventId, e);
            }

            // 7. Persist PENDING booking
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
                    .holdExpiresAt(Instant.now().plus(HOLD_DURATION))
                    .build();

            BookingEntity saved = bookingRepository.save(booking);
            log.info("Booking created: id='{}', event='{}', category='{}', qty={}, total={}",
                    saved.getId(), eventId, seatCategory, quantity, totalPrice);

            // 8. (Future P3-05: publish BookingCreatedEvent to Kafka here)

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
                Math.min(Math.max(pageSize, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        if (statusFilter != null && !statusFilter.isBlank()) {
            BookingStatus status = BookingStatus.valueOf(statusFilter);
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

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(reason);

        BookingEntity saved = bookingRepository.save(booking);

        // Release seats back to event-service (best-effort)
        try {
            eventServiceClient.updateSeatAvailability(
                    booking.getEventId(), booking.getSeatCategory(), booking.getQuantity());
        } catch (Exception e) {
            log.error("Failed to release seats for cancelled booking '{}': {}",
                    bookingId, e.getMessage());
            // Seats will be recovered by eventual consistency / reconciliation
        }

        log.info("Booking cancelled: id='{}', reason='{}'", bookingId, reason);
        return saved;
    }

    // ─── Private helpers ─────────────────────────────────────────────

    private BookingServiceException mapGrpcException(String eventId, StatusRuntimeException e) {
        log.error("Event-service gRPC call failed: {}", e.getStatus(), e);
        return switch (e.getStatus().getCode()) {
            case NOT_FOUND -> new EventNotAvailableException(eventId, "Event not found");
            case FAILED_PRECONDITION -> new EventNotAvailableException(eventId,
                    e.getStatus().getDescription());
            default -> new EventNotAvailableException(eventId,
                    "Event service error: " + e.getStatus().getDescription());
        };
    }
}
