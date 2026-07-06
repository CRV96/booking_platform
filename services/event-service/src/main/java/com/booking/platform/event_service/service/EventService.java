package com.booking.platform.event_service.service;

import com.booking.platform.common.grpc.event.CreateEventRequest;
import com.booking.platform.common.grpc.event.SearchEventsRequest;
import com.booking.platform.common.grpc.event.UpdateEventRequest;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.dto.OrganizerDto;

import java.util.List;

/**
 * Core service interface for managing events. Defines all operations related to event lifecycle, including creation, retrieval, updates, publishing, cancellation, searching, and seat availability management.
 */
public interface EventService {

    /** Creates a new event in DRAFT status and publishes an EVENT_CREATED Kafka event. */
    EventDocument createEvent(CreateEventRequest request, OrganizerDto organizer);

    /** Retrieves an event by ID. Throws {@link com.booking.platform.event_service.exception.EventNotFoundException} if not found. */
    EventDocument getEvent(String eventId);

    /** Updates mutable fields of an event. Only DRAFT and PUBLISHED events can be updated. */
    EventDocument updateEvent(String eventId, UpdateEventRequest request);

    /** Transitions a DRAFT event to PUBLISHED status. Validates readiness before publishing. */
    EventDocument publishEvent(String eventId);

    /** Cancels a DRAFT or PUBLISHED event. Only DRAFT and PUBLISHED events can be cancelled. */
    EventDocument cancelEvent(String eventId, String reason);

    /** Searches published events by query, category, city, and date range with pagination. */
    List<EventDocument> searchEvents(SearchEventsRequest request);

    /** Atomically increments/decrements available seats for a category. Used by booking-service. */
    EventDocument updateSeatAvailability(String eventId, String seatCategoryName, int delta);
}
