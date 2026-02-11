package com.booking.platform.event_service.service.impl;

import com.booking.platform.common.grpc.event.CreateEventRequest;
import com.booking.platform.common.grpc.event.SearchEventsRequest;
import com.booking.platform.common.grpc.event.SeatCategoryInfo;
import com.booking.platform.common.grpc.event.UpdateEventRequest;
import com.booking.platform.event_service.document.*;
import com.booking.platform.event_service.dto.OrganizerDto;
import com.booking.platform.event_service.exception.EventNotFoundException;
import com.booking.platform.event_service.exception.InsufficientSeatsException;
import com.booking.platform.event_service.exception.InvalidEventStateException;
import com.booking.platform.event_service.exception.ValidationException;
import com.booking.platform.event_service.repository.EventRepository;
import com.booking.platform.event_service.service.EventService;
import com.booking.platform.event_service.validator.EventValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final MongoTemplate mongoTemplate;
    private final EventValidator eventValidator;

    @Override
    public EventDocument createEvent(CreateEventRequest request, OrganizerDto organizer) {

        log.debug("Creating event '{}' for organizer '{}'", request.getTitle(), organizer.userId());

        eventValidator.validateCreateRequest(request);
        
        EventDocument saved = eventRepository.save(getEventDocument(request, organizer));

        log.info("Event created: id='{}', title='{}'", saved.getId(), saved.getTitle());

        return saved;
    }

    @Override
    public EventDocument getEvent(String eventId) {
        log.debug("Fetching event by ID: {}", eventId);

        return eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    @Override
    public EventDocument updateEvent(String eventId, UpdateEventRequest request) {
        log.debug("Updating event '{}'", eventId);

        EventDocument event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.COMPLETED) {
            throw new InvalidEventStateException(
                    "Cannot update event in status: " + event.getStatus());
        }

        if (request.hasTitle())       event.setTitle(request.getTitle());
        if (request.hasDescription()) event.setDescription(request.getDescription());
        if (request.hasCategory())    event.setCategory(EventCategory.valueOf(request.getCategory()));
        if (request.hasDateTime())    event.setDateTime(Instant.parse(request.getDateTime()));
        if (request.hasEndDateTime()) event.setEndDateTime(Instant.parse(request.getEndDateTime()));
        if (request.hasTimezone())    event.setTimezone(request.getTimezone());

        if (request.hasVenue()) {
            event.setVenue(getVenueInfo(request.getVenue()));
        }

        if (!request.getSeatCategoriesList().isEmpty()) {
            event.setSeatCategories(getSeatCategories(request.getSeatCategoriesList()));
        }

        if (!request.getImagesList().isEmpty()) event.setImages(new ArrayList<>(request.getImagesList()));
        if (!request.getTagsList().isEmpty())   event.setTags(new ArrayList<>(request.getTagsList()));

        EventDocument saved = eventRepository.save(event);
        log.info("Event updated: id='{}'", saved.getId());
        return saved;
    }

    @Override
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
        log.info("Event published: id='{}', title='{}'", saved.getId(), saved.getTitle());
        return saved;
    }

    @Override
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
        log.info("Event cancelled: id='{}', reason='{}'", saved.getId(), reason);
        return saved;
    }

    @Override
    public List<EventDocument> searchEvents(SearchEventsRequest request) {
        log.debug("Searching events: query='{}', category='{}', city='{}', page={}",
                request.getQuery(), request.getCategory(), request.getCity(), request.getPage());

        List<Criteria> criteriaList = getCriteriaList(request);

        Query query = new Query(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));

        // Full-text search — applied separately via TextCriteria when a query string is present
        if (request.hasQuery() && !request.getQuery().isBlank()) {
            query.addCriteria(TextCriteria.forDefaultLanguage().matchingAny(request.getQuery()));
        }

        int page = Math.max(request.getPage(), 0);
        int pageSize = request.getPageSize() > 0 ? Math.min(request.getPageSize(), 100) : 20;
        query.skip((long) page * pageSize).limit(pageSize);

        return mongoTemplate.find(query, EventDocument.class);
    }

    @Override
    public EventDocument updateSeatAvailability(String eventId, String seatCategoryName, int delta) {
        log.debug("Updating seat availability: event='{}', category='{}', delta={}",
                eventId, seatCategoryName, delta);

        // Atomically apply delta to availableSeats using $inc.
        // For decrements (delta < 0) we first check there are enough seats.
        if (delta < 0) {
            Query checkQuery = new Query(
                    Criteria.where("_id").is(eventId)
                            .and("seatCategories")
                            .elemMatch(
                                    Criteria.where("name").is(seatCategoryName)
                                            .and("availableSeats").gte(-delta)
                            )
            );

            boolean hasEnoughSeats = mongoTemplate.exists(checkQuery, EventDocument.class);
            if (!hasEnoughSeats) {
                throw new InsufficientSeatsException(eventId, seatCategoryName, -delta, 0);
            }
        }

        Query query = new Query(
                Criteria.where("_id").is(eventId)
                        .and("seatCategories.name").is(seatCategoryName)
        );

        Update update = new Update().inc("seatCategories.$.availableSeats", delta);

        EventDocument updated = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                EventDocument.class
        );

        if (updated == null) {
            throw new EventNotFoundException(eventId);
        }

        log.info("Seat availability updated: event='{}', category='{}', delta={}",
                eventId, seatCategoryName, delta);
        return updated;
    }

    private EventDocument getEventDocument(CreateEventRequest request, OrganizerDto organizer) {
        return EventDocument.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .category(EventCategory.valueOf(request.getCategory()))
                .status(EventStatus.DRAFT)
                .venue(getVenueInfo(request.getVenue()))
                .dateTime(Instant.parse(request.getDateTime()))
                .endDateTime(request.getEndDateTime().isBlank() ? null : Instant.parse(request.getEndDateTime()))
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
                        .availableSeats(sc.getTotalSeats()) // available starts equal to total
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

        // Always restrict to PUBLISHED events for public search
        criteriaList.add(Criteria.where("status").is(EventStatus.PUBLISHED));

        if (request.hasCategory() && !request.getCategory().isBlank()) {
            criteriaList.add(Criteria.where("category").is(EventCategory.valueOf(request.getCategory())));
        }

        if (request.hasCity() && !request.getCity().isBlank()) {
            criteriaList.add(Criteria.where("venue.city").regex(request.getCity(), "i"));
        }

        if (request.hasDateFrom() && !request.getDateFrom().isBlank()) {
            criteriaList.add(Criteria.where("dateTime").gte(Instant.parse(request.getDateFrom())));
        }

        if (request.hasDateTo() && !request.getDateTo().isBlank()) {
            criteriaList.add(Criteria.where("dateTime").lte(Instant.parse(request.getDateTo())));
        }

        return criteriaList;
    }

}
