package com.booking.platform.event_service.service;

import com.booking.platform.event_service.base.BaseIntegrationTest;
import com.booking.platform.event_service.document.enums.EventCategory;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.document.enums.EventStatus;
import com.booking.platform.event_service.document.OrganizerInfo;
import com.booking.platform.event_service.document.SeatCategory;
import com.booking.platform.event_service.document.VenueInfo;
import com.booking.platform.event_service.exception.EventNotFoundException;
import com.booking.platform.event_service.exception.InsufficientSeatsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for seat availability management.
 *
 * <p>Key scenario: 100 threads try to book the last 10 seats concurrently.
 * Exactly 10 should succeed and 90 should fail — no overselling.
 *
 * <p>Uses MongoDB's atomic {@code $inc} via {@code findAndModify} to guarantee
 * correctness under concurrent load without application-level locking.
 */
@DisplayName("SeatAvailability Integration Tests")
class SeatAvailabilityIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private EventService eventService;

    // =========================================================================
    // Helpers
    // =========================================================================

    private EventDocument saveEventWithSeats(int totalSeats, int availableSeats) {
        return eventRepository.save(EventDocument.builder()
                .title("Seat Test Event")
                .category(EventCategory.CONCERT)
                .status(EventStatus.PUBLISHED)
                .dateTime(futureDate(5))
                .timezone("UTC")
                .venue(VenueInfo.builder()
                        .name("Arena")
                        .city("Bucharest")
                        .country("Romania")
                        .build())
                .organizer(OrganizerInfo.builder()
                        .userId("user-001")
                        .name("Alice")
                        .email("alice@example.com")
                        .build())
                .seatCategories(List.of(SeatCategory.builder()
                        .name("General")
                        .price(50.0)
                        .currency("USD")
                        .totalSeats(totalSeats)
                        .availableSeats(availableSeats)
                        .build()))
                .build());
    }

    private int getAvailableSeats(String eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow()
                .getSeatCategories()
                .get(0)
                .getAvailableSeats();
    }

    // =========================================================================
    // Basic seat operations
    // =========================================================================

    @Nested
    @DisplayName("basic operations")
    class BasicOperations {

        @Test
        @DisplayName("decrements available seats by delta")
        void updateSeatAvailability_decrements() {
            EventDocument event = saveEventWithSeats(100, 100);

            eventService.updateSeatAvailability(event.getId(), "General", -5);

            assertThat(getAvailableSeats(event.getId())).isEqualTo(95);
        }

        @Test
        @DisplayName("increments available seats by delta (release)")
        void updateSeatAvailability_increments() {
            EventDocument event = saveEventWithSeats(100, 50);

            eventService.updateSeatAvailability(event.getId(), "General", 10);

            assertThat(getAvailableSeats(event.getId())).isEqualTo(60);
        }

        @Test
        @DisplayName("evicts detail cache after seat update")
        void updateSeatAvailability_evictsCache() {
            EventDocument event = saveEventWithSeats(100, 100);

            // Populate cache first
            eventService.getEvent(event.getId());
            assertThat(cacheManager.getCache("event:detail").get(event.getId())).isNotNull();

            // Update seats — should evict cache
            eventService.updateSeatAvailability(event.getId(), "General", -1);

            assertThat(cacheManager.getCache("event:detail").get(event.getId())).isNull();
        }

        @Test
        @DisplayName("throws InsufficientSeatsException when not enough seats available")
        void updateSeatAvailability_insufficientSeats_throwsException() {
            EventDocument event = saveEventWithSeats(100, 5);

            assertThatThrownBy(() ->
                    eventService.updateSeatAvailability(event.getId(), "General", -10))
                    .isInstanceOf(InsufficientSeatsException.class);
        }

        @Test
        @DisplayName("throws EventNotFoundException for unknown event")
        void updateSeatAvailability_unknownEvent_throwsException() {
            assertThatThrownBy(() ->
                    eventService.updateSeatAvailability("non-existent-id", "General", -1))
                    .isInstanceOf(EventNotFoundException.class);
        }
    }

    // =========================================================================
    // Concurrent seat decrement — the critical no-overselling test
    // =========================================================================

    @Nested
    @DisplayName("concurrent seat booking")
    class ConcurrentBooking {

        @Test
        @DisplayName("100 threads compete for 10 seats — exactly 10 succeed, no overselling")
        void concurrentBooking_noOverselling() throws InterruptedException {
            // Arrange — 10 seats available
            EventDocument event = saveEventWithSeats(10, 10);
            String eventId = event.getId();

            int threadCount = 100;
            CountDownLatch startLatch = new CountDownLatch(1);  // all threads start at once
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            List<Throwable> unexpectedErrors = new ArrayList<>();

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // Act — all 100 threads try to decrement by 1 simultaneously
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await(); // wait for all threads to be ready
                        eventService.updateSeatAvailability(eventId, "General", -1);
                        successCount.incrementAndGet();
                    } catch (InsufficientSeatsException e) {
                        failureCount.incrementAndGet();
                    } catch (Exception e) {
                        unexpectedErrors.add(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown(); // release all threads simultaneously
            doneLatch.await();      // wait for all threads to finish
            executor.shutdown();

            // Assert
            assertThat(unexpectedErrors)
                    .as("No unexpected errors should occur")
                    .isEmpty();

            assertThat(successCount.get())
                    .as("Exactly 10 bookings should succeed (one per available seat)")
                    .isEqualTo(10);

            assertThat(failureCount.get())
                    .as("Exactly 90 bookings should fail with InsufficientSeatsException")
                    .isEqualTo(90);

            assertThat(getAvailableSeats(eventId))
                    .as("Available seats should be 0 after 10 successful bookings")
                    .isEqualTo(0);
        }

        @Test
        @DisplayName("seat count never goes below zero under concurrent load")
        void concurrentBooking_seatsNeverNegative() throws InterruptedException {
            EventDocument event = saveEventWithSeats(5, 5);
            String eventId = event.getId();

            int threadCount = 50;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        eventService.updateSeatAvailability(eventId, "General", -1);
                    } catch (InsufficientSeatsException ignored) {
                        // expected for most threads
                    } catch (Exception e) {
                        // unexpected — but we still want count to go down
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            // Available seats must never be negative
            assertThat(getAvailableSeats(eventId))
                    .as("Available seats must never go negative")
                    .isGreaterThanOrEqualTo(0);
        }
    }
}
