package com.booking.platform.event_service.service;

import com.booking.platform.common.grpc.event.CreateEventRequest;
import com.booking.platform.common.grpc.event.SearchEventsRequest;
import com.booking.platform.common.grpc.event.UpdateEventRequest;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.dto.OrganizerDto;

import java.util.List;

public interface EventService {

    EventDocument createEvent(CreateEventRequest request, OrganizerDto organizer);

    EventDocument getEvent(String eventId);

    EventDocument updateEvent(String eventId, UpdateEventRequest request);

    EventDocument publishEvent(String eventId);

    EventDocument cancelEvent(String eventId, String reason);

    List<EventDocument> searchEvents(SearchEventsRequest request);

    EventDocument updateSeatAvailability(String eventId, String seatCategoryName, int delta);
}
