package com.booking.platform.event_service.service;

import com.booking.platform.common.grpc.event.SearchEventsRequest;
import com.booking.platform.event_service.base.BaseIntegrationTest;
import com.booking.platform.event_service.document.EventCategory;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.document.EventStatus;
import com.booking.platform.event_service.document.OrganizerInfo;
import com.booking.platform.event_service.document.SeatCategory;
import com.booking.platform.event_service.document.VenueInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for event search — covers category filter, city filter,
 * date range filter, text search, combined filters, and pagination.
 *
 * <p>Only PUBLISHED events should be returned by any search.
 */
@DisplayName("EventSearch Integration Tests")
class EventSearchIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private EventService eventService;

    // =========================================================================
    // Seed data — set up before each test
    // =========================================================================

    @BeforeEach
    void seedEvents() {
        // Concert in Bucharest — future
        saveEvent("Rock Night", EventCategory.CONCERT, EventStatus.PUBLISHED,
                "Bucharest", "Romania", futureDate(5));

        // Concert in London — future
        saveEvent("Jazz Evening", EventCategory.CONCERT, EventStatus.PUBLISHED,
                "London", "UK", futureDate(10));

        // Sports in Bucharest — future
        saveEvent("Football Final", EventCategory.SPORTS, EventStatus.PUBLISHED,
                "Bucharest", "Romania", futureDate(3));

        // Conference in Berlin — future
        saveEvent("Tech Summit", EventCategory.CONFERENCE, EventStatus.PUBLISHED,
                "Berlin", "Germany", futureDate(20));

        // DRAFT — should NEVER appear in search results
        saveEvent("Hidden Draft", EventCategory.CONCERT, EventStatus.DRAFT,
                "Bucharest", "Romania", futureDate(7));

        // CANCELLED — should NEVER appear in search results
        saveEvent("Cancelled Show", EventCategory.CONCERT, EventStatus.CANCELLED,
                "Bucharest", "Romania", futureDate(7));
    }

    private void saveEvent(String title, EventCategory category, EventStatus status,
                           String city, String country, java.time.Instant dateTime) {
        eventRepository.save(EventDocument.builder()
                .title(title)
                .description("Description for " + title)
                .category(category)
                .status(status)
                .dateTime(dateTime)
                .timezone("UTC")
                .venue(VenueInfo.builder()
                        .name("Venue")
                        .city(city)
                        .country(country)
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
                        .totalSeats(100)
                        .availableSeats(100)
                        .build()))
                .build());
    }

    // =========================================================================
    // No filter — all published
    // =========================================================================

    @Nested
    @DisplayName("no filters")
    class NoFilters {

        @Test
        @DisplayName("returns only PUBLISHED events — excludes DRAFT and CANCELLED")
        void search_noFilters_returnsOnlyPublished() {
            List<EventDocument> results = eventService.searchEvents(
                    SearchEventsRequest.newBuilder().build());

            assertThat(results).hasSize(4); // only the 4 published ones
            assertThat(results).allMatch(e -> e.getStatus() == EventStatus.PUBLISHED);
        }
    }

    // =========================================================================
    // Category filter
    // =========================================================================

    @Nested
    @DisplayName("filter by category")
    class CategoryFilter {

        @Test
        @DisplayName("returns only CONCERT events")
        void search_byCategory_returnsConcertsOnly() {
            List<EventDocument> results = eventService.searchEvents(
                    SearchEventsRequest.newBuilder()
                            .setCategory("CONCERT")
                            .build());

            assertThat(results).hasSize(2);
            assertThat(results).allMatch(e -> e.getCategory() == EventCategory.CONCERT);
        }

        @Test
        @DisplayName("returns only SPORTS events")
        void search_bySportsCategory_returnsSportsOnly() {
            List<EventDocument> results = eventService.searchEvents(
                    SearchEventsRequest.newBuilder()
                            .setCategory("SPORTS")
                            .build());

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getTitle()).isEqualTo("Football Final");
        }
    }

    // =========================================================================
    // City filter
    // =========================================================================

    @Nested
    @DisplayName("filter by city")
    class CityFilter {

        @Test
        @DisplayName("returns events in Bucharest only")
        void search_byCity_returnsBucharestEvents() {
            List<EventDocument> results = eventService.searchEvents(
                    SearchEventsRequest.newBuilder()
                            .setCity("Bucharest")
                            .build());

            assertThat(results).hasSize(2); // Rock Night + Football Final (not Draft/Cancelled)
            assertThat(results).allMatch(e -> e.getVenue().getCity().equalsIgnoreCase("Bucharest"));
        }

        @Test
        @DisplayName("city filter is case-insensitive")
        void search_byCity_isCaseInsensitive() {
            List<EventDocument> results = eventService.searchEvents(
                    SearchEventsRequest.newBuilder()
                            .setCity("bucharest")
                            .build());

            assertThat(results).hasSize(2);
        }
    }

    // =========================================================================
    // Date range filter
    // =========================================================================

    @Nested
    @DisplayName("filter by date range")
    class DateRangeFilter {

        @Test
        @DisplayName("returns events within date range")
        void search_byDateRange_returnsEventsInRange() {
            // Only events in next 6 days: Rock Night (+5) and Football Final (+3)
            String dateFrom = futureDate(1).toString();
            String dateTo = futureDate(6).toString();

            List<EventDocument> results = eventService.searchEvents(
                    SearchEventsRequest.newBuilder()
                            .setDateFrom(dateFrom)
                            .setDateTo(dateTo)
                            .build());

            assertThat(results).hasSize(2);
            assertThat(results).extracting(EventDocument::getTitle)
                    .containsExactlyInAnyOrder("Rock Night", "Football Final");
        }

        @Test
        @DisplayName("returns empty list when no events in date range")
        void search_byDateRange_noResults() {
            // Far future — no events seeded there
            String dateFrom = futureDate(50).toString();
            String dateTo = futureDate(60).toString();

            List<EventDocument> results = eventService.searchEvents(
                    SearchEventsRequest.newBuilder()
                            .setDateFrom(dateFrom)
                            .setDateTo(dateTo)
                            .build());

            assertThat(results).isEmpty();
        }
    }

    // =========================================================================
    // Combined filters
    // =========================================================================

    @Nested
    @DisplayName("combined filters")
    class CombinedFilters {

        @Test
        @DisplayName("category + city filter returns precise results")
        void search_categoryAndCity_returnsPreciseResults() {
            List<EventDocument> results = eventService.searchEvents(
                    SearchEventsRequest.newBuilder()
                            .setCategory("CONCERT")
                            .setCity("Bucharest")
                            .build());

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getTitle()).isEqualTo("Rock Night");
        }

        @Test
        @DisplayName("returns empty list when filter combination matches nothing")
        void search_combinedFilters_noResults() {
            List<EventDocument> results = eventService.searchEvents(
                    SearchEventsRequest.newBuilder()
                            .setCategory("SPORTS")
                            .setCity("London")
                            .build());

            assertThat(results).isEmpty();
        }
    }

    // =========================================================================
    // Text search
    // =========================================================================

    @Nested
    @DisplayName("text search")
    class TextSearch {

        @Test
        @DisplayName("text search finds events by title keyword")
        void search_byText_findsMatchingTitle() {
            List<EventDocument> results = eventService.searchEvents(
                    SearchEventsRequest.newBuilder()
                            .setQuery("Rock")
                            .build());

            assertThat(results).isNotEmpty();
            assertThat(results).anyMatch(e -> e.getTitle().contains("Rock"));
        }

        @Test
        @DisplayName("text search finds events by description keyword")
        void search_byText_findsMatchingDescription() {
            List<EventDocument> results = eventService.searchEvents(
                    SearchEventsRequest.newBuilder()
                            .setQuery("Tech Summit")
                            .build());

            assertThat(results).isNotEmpty();
            assertThat(results).anyMatch(e -> e.getTitle().equals("Tech Summit"));
        }
    }

    // =========================================================================
    // Pagination
    // =========================================================================

    @Nested
    @DisplayName("pagination")
    class Pagination {

        @Test
        @DisplayName("pageSize limits the number of results returned")
        void search_withPageSize_limitsResults() {
            List<EventDocument> results = eventService.searchEvents(
                    SearchEventsRequest.newBuilder()
                            .setPage(0)
                            .setPageSize(2)
                            .build());

            assertThat(results).hasSize(2);
        }

        @Test
        @DisplayName("page 1 returns different results than page 0")
        void search_secondPage_returnsDifferentResults() {
            List<EventDocument> page0 = eventService.searchEvents(
                    SearchEventsRequest.newBuilder().setPage(0).setPageSize(2).build());
            List<EventDocument> page1 = eventService.searchEvents(
                    SearchEventsRequest.newBuilder().setPage(1).setPageSize(2).build());

            List<String> ids0 = page0.stream().map(EventDocument::getId).toList();
            List<String> ids1 = page1.stream().map(EventDocument::getId).toList();

            // No overlap between pages
            assertThat(ids0).doesNotContainAnyElementsOf(ids1);
        }
    }
}
