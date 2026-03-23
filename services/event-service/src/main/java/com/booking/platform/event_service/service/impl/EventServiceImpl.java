package com.booking.platform.event_service.service.impl;

import com.booking.platform.common.grpc.event.CreateEventRequest;
import com.booking.platform.common.grpc.event.SearchEventsRequest;
import com.booking.platform.common.grpc.event.SeatCategoryInfo;
import com.booking.platform.common.grpc.event.UpdateEventRequest;
import com.booking.platform.event_service.config.CacheConfig;
import com.booking.platform.event_service.constants.DocumentConst;
import com.booking.platform.event_service.document.*;
import com.booking.platform.event_service.document.enums.EventCategory;
import com.booking.platform.event_service.document.enums.EventStatus;
import com.booking.platform.event_service.dto.OrganizerDto;
import com.booking.platform.event_service.exception.EventNotFoundException;
import com.booking.platform.event_service.exception.InsufficientSeatsException;
import com.booking.platform.event_service.exception.InvalidEventStateException;
import com.booking.platform.event_service.exception.ValidationException;
import com.booking.platform.event_service.messaging.publisher.EventPublisher;
import com.booking.platform.event_service.properties.EventProperties;
import com.booking.platform.event_service.repository.EventRepository;
import com.booking.platform.event_service.service.EventService;
import com.booking.platform.event_service.validator.EventValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final MongoTemplate mongoTemplate;
    private final EventValidator eventValidator;
    private final EventPublisher eventPublisher;
    private final EventProperties eventProperties;

    // =========================================================================
    // CREATE — no cache on creation, event starts as DRAFT (not publicly cached)
    // =========================================================================

    @Override
    public EventDocument createEvent(CreateEventRequest request, OrganizerDto organizer) {
        log.debug("Creating event '{}' for organizer '{}'", request.getTitle(), organizer.userId());

        eventValidator.validateCreateRequest(request);

        EventDocument saved = eventRepository.save(getEventDocument(request, organizer));

        eventPublisher.publishEventCreated(saved);
        log.info("Event created: id='{}', title='{}'", saved.getId(), saved.getTitle());
        return saved;
    }

    // =========================================================================
    // GET — cache individual event details for 5 minutes
    // =========================================================================

    @Override
    @Cacheable(value = CacheConfig.CACHE_EVENT_DETAIL, key = "#a0")
    public EventDocument getEvent(String eventId) {
        log.debug("Fetching event by ID: {} (cache miss)", eventId);

        return eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    // =========================================================================
    // UPDATE — evict detail cache + search cache on any update
    // =========================================================================

    @Override
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.CACHE_EVENT_DETAIL, key = "#a0"),
            @CacheEvict(value = CacheConfig.CACHE_EVENTS_SEARCH, allEntries = true),
            @CacheEvict(value = CacheConfig.CACHE_EVENTS_FEATURED, allEntries = true)
    })
    public EventDocument updateEvent(String eventId, UpdateEventRequest request) {
        log.debug("Updating event '{}'", eventId);

        EventDocument event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.COMPLETED) {
            throw new InvalidEventStateException(
                    "Cannot update event in status: " + event.getStatus());
        }

        List<String> changedFields = new ArrayList<>();

        if (request.hasTitle())       { event.setTitle(request.getTitle());                                    changedFields.add(DocumentConst.Event.TITLE); }
        if (request.hasDescription()) { event.setDescription(request.getDescription());                        changedFields.add(DocumentConst.Event.DESCRIPTION); }
        if (request.hasCategory())    { event.setCategory(EventCategory.valueOf(request.getCategory()));       changedFields.add(DocumentConst.Event.CATEGORY); }
        if (request.hasDateTime())    { event.setDateTime(eventValidator.parseInstant(request.getDateTime(), DocumentConst.Event.DATE_TIME));    changedFields.add(DocumentConst.Event.DATE_TIME); }
        if (request.hasEndDateTime()) { event.setEndDateTime(eventValidator.parseInstant(request.getEndDateTime(), DocumentConst.Event.END_DATE_TIME)); changedFields.add(DocumentConst.Event.END_DATE_TIME); }
        if (request.hasTimezone())    { event.setTimezone(request.getTimezone());                              changedFields.add(DocumentConst.Event.TIMEZONE); }

        if (request.hasVenue()) {
            event.setVenue(getVenueInfo(request.getVenue()));
            changedFields.add(DocumentConst.Event.VENUE);
        }

        if (!request.getSeatCategoriesList().isEmpty()) {
            event.setSeatCategories(getSeatCategories(request.getSeatCategoriesList()));
            changedFields.add(DocumentConst.Event.SEAT_CATEGORIES);
        }

        if (!request.getImagesList().isEmpty()) { event.setImages(new ArrayList<>(request.getImagesList())); changedFields.add(DocumentConst.Event.IMAGES); }
        if (!request.getTagsList().isEmpty())   { event.setTags(new ArrayList<>(request.getTagsList()));     changedFields.add(DocumentConst.Event.TAGS); }

        // Validate endDateTime > dateTime if both are present
        if (event.getDateTime() != null && event.getEndDateTime() != null
                && !event.getEndDateTime().isAfter(event.getDateTime())) {
            throw new ValidationException("endDateTime must be after dateTime");
        }

        EventDocument saved = eventRepository.save(event);
        eventPublisher.publishEventUpdated(saved, changedFields);
        log.info("Event updated: id='{}'", saved.getId());
        return saved;
    }

    // =========================================================================
    // PUBLISH — put updated document into cache immediately after publishing
    // =========================================================================

    @Override
    @Caching(
            put = {
                    @CachePut(value = CacheConfig.CACHE_EVENT_DETAIL, key = "#a0")
            },
            evict = {
                    @CacheEvict(value = CacheConfig.CACHE_EVENTS_SEARCH, allEntries = true),
                    @CacheEvict(value = CacheConfig.CACHE_EVENTS_FEATURED, allEntries = true)
            }
    )
    public EventDocument publishEvent(String eventId) {
        log.debug("Publishing event '{}'", eventId);

        EventDocument event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        if (event.getStatus() != EventStatus.DRAFT) {
            throw new InvalidEventStateException(
                    "Only DRAFT events can be published. Current status: " + event.getStatus());
        }

        eventValidator.validateForPublish(event);

        event.setStatus(EventStatus.PUBLISHED);

        EventDocument saved = eventRepository.save(event);
        eventPublisher.publishEventPublished(saved);
        log.info("Event published: id='{}', title='{}'", saved.getId(), saved.getTitle());
        return saved;
    }

    // =========================================================================
    // CANCEL — evict all related caches
    // =========================================================================

    @Override
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.CACHE_EVENT_DETAIL, key = "#a0"),
            @CacheEvict(value = CacheConfig.CACHE_EVENTS_SEARCH, allEntries = true),
            @CacheEvict(value = CacheConfig.CACHE_EVENTS_FEATURED, allEntries = true)
    })
    public EventDocument cancelEvent(String eventId, String reason) {
        log.debug("Cancelling event '{}', reason: {}", eventId, reason);

        EventDocument event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.PUBLISHED) {
            throw new InvalidEventStateException(
                    "Only DRAFT or PUBLISHED events can be cancelled. Current status: " + event.getStatus());
        }

        event.setStatus(EventStatus.CANCELLED);

        EventDocument saved = eventRepository.save(event);
        eventPublisher.publishEventCancelled(saved, reason);
        log.info("Event cancelled: id='{}', reason='{}'", saved.getId(), reason);
        return saved;
    }

    // =========================================================================
    // SEARCH — cached by a hash of the request filters
    // =========================================================================

    @Override
    @Cacheable(value = CacheConfig.CACHE_EVENTS_SEARCH, key = "#a0.hashCode()")
    public List<EventDocument> searchEvents(SearchEventsRequest request) {
        log.debug("Searching events: query='{}', category='{}', city='{}', page={} (cache miss)",
                request.getQuery(), request.getCategory(), request.getCity(), request.getPage());

        List<Criteria> criteriaList = getCriteriaList(request);

        Query query = new Query(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));

        if (request.hasQuery() && !request.getQuery().isBlank()) {
            query.addCriteria(TextCriteria.forDefaultLanguage().matchingAny(request.getQuery()));
        }

        EventProperties.Pagination pagination = eventProperties.pagination();
        int page = Math.max(request.getPage(), 0);
        int pageSize = request.getPageSize() > 0
                ? Math.min(request.getPageSize(), pagination.maxPageSize())
                : pagination.defaultPageSize();

        // Cap the total offset to prevent expensive skip operations on large datasets
        long skip = Math.min((long) page * pageSize, pagination.maxSkippableResults());
        query.skip(skip).limit(pageSize);

        return mongoTemplate.find(query, EventDocument.class);
    }

    // =========================================================================
    // UPDATE SEAT AVAILABILITY — evict detail cache since seat counts changed
    // =========================================================================

    @Override
    @CacheEvict(value = CacheConfig.CACHE_EVENT_DETAIL, key = "#a0")
    public EventDocument updateSeatAvailability(String eventId, String seatCategoryName, int delta) {
        log.debug("Updating seat availability: event='{}', category='{}', delta={}",
                eventId, seatCategoryName, delta);

        // Build the atomic query. For decrements, embed the seat-availability guard
        // directly in the filter so the check and the update are a single MongoDB
        // operation — this eliminates the TOCTOU race that causes overselling.
        Criteria queryCriteria = Criteria.where(DocumentConst.Event.ID).is(eventId);
        if (delta < 0) {
            queryCriteria = queryCriteria.and(DocumentConst.Event.SEAT_CATEGORIES).elemMatch(
                    Criteria.where(DocumentConst.Event.SEAT_CATEGORIES_NAME).is(seatCategoryName)
                            .and(DocumentConst.Event.SEAT_CATEGORIES_AVAILABLE_SEATS).gte(-delta)
            );
        } else {
            queryCriteria = queryCriteria.and(DocumentConst.Event.SEAT_CATEGORIES_DOT_NAME).is(seatCategoryName);
        }

        Query query = new Query(queryCriteria);
        Update update = new Update().inc(DocumentConst.Event.SEAT_CATEGORIES_POSITIONAL_AVAILABLE_SEATS, delta);

        EventDocument updated = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                EventDocument.class
        );

        if (updated == null) {
            // Distinguish between unknown event and insufficient seats (cold error path only).
            if (!eventRepository.existsById(eventId)) {
                throw new EventNotFoundException(eventId);
            }
            throw new InsufficientSeatsException(eventId, seatCategoryName, -delta, 0);
        }

        log.info("Seat availability updated: event='{}', category='{}', delta={}",
                eventId, seatCategoryName, delta);
        return updated;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private EventDocument getEventDocument(CreateEventRequest request, OrganizerDto organizer) {
        return EventDocument.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .category(EventCategory.valueOf(request.getCategory()))
                .status(EventStatus.DRAFT)
                .venue(getVenueInfo(request.getVenue()))
                .dateTime(eventValidator.parseInstant(request.getDateTime(), "dateTime"))
                .endDateTime(request.getEndDateTime().isBlank() ? null : eventValidator.parseInstant(request.getEndDateTime(), "endDateTime"))
                .timezone(request.getTimezone())
                .organizer(getOrganizer(organizer))
                .seatCategories(getSeatCategories(request.getSeatCategoriesList()))
                .images(new ArrayList<>(request.getImagesList()))
                .tags(new ArrayList<>(request.getTagsList()))
                .build();
    }

    private OrganizerInfo getOrganizer(OrganizerDto organizer) {
        return OrganizerInfo.builder()
                .userId(organizer.userId())
                .name(organizer.name())
                .email(organizer.email())
                .build();
    }

    private List<SeatCategory> getSeatCategories(List<SeatCategoryInfo> seatCategoriesList) {
        return seatCategoriesList.stream()
                .map(sc -> SeatCategory.builder()
                        .name(sc.getName())
                        .price(sc.getPrice())
                        .currency(sc.getCurrency())
                        .totalSeats(sc.getTotalSeats())
                        .availableSeats(sc.getTotalSeats())
                        .build())
                .toList();
    }

    private VenueInfo getVenueInfo(com.booking.platform.common.grpc.event.VenueInfo venue) {
        return VenueInfo.builder()
                .name(venue.getName())
                .address(venue.getAddress())
                .city(venue.getCity())
                .country(venue.getCountry())
                .latitude(venue.hasLatitude() ? venue.getLatitude() : null)
                .longitude(venue.hasLongitude() ? venue.getLongitude() : null)
                .capacity(venue.hasCapacity() ? venue.getCapacity() : null)
                .build();
    }

    private List<Criteria> getCriteriaList(SearchEventsRequest request) {
        List<Criteria> criteriaList = new ArrayList<>();

        criteriaList.add(Criteria.where(DocumentConst.Event.STATUS).is(EventStatus.PUBLISHED));

        if (request.hasCategory() && !request.getCategory().isBlank()) {
            criteriaList.add(Criteria.where(DocumentConst.Event.CATEGORY).is(EventCategory.valueOf(request.getCategory())));
        }

        if (request.hasCity() && !request.getCity().isBlank()) {
            criteriaList.add(Criteria.where(DocumentConst.Event.VENUE_CITY).regex(request.getCity(), "i"));
        }

        if (request.hasDateFrom() && !request.getDateFrom().isBlank()) {
            criteriaList.add(Criteria.where(DocumentConst.Event.DATE_TIME).gte(eventValidator.parseInstant(request.getDateFrom(), "dateFrom")));
        }

        if (request.hasDateTo() && !request.getDateTo().isBlank()) {
            criteriaList.add(Criteria.where(DocumentConst.Event.DATE_TIME).lte(eventValidator.parseInstant(request.getDateTo(), "dateTo")));
        }

        return criteriaList;
    }
}
