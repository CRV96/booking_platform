package com.booking.platform.graphql_gateway.grpc.client;

import com.booking.platform.common.grpc.event.EventResponse;
import com.booking.platform.common.grpc.event.SearchEventsResponse;
import com.booking.platform.graphql_gateway.dto.event.EventCreateRequest;
import com.booking.platform.graphql_gateway.dto.event.EventSearchRequest;
import com.booking.platform.graphql_gateway.dto.event.EventUpdateRequest;

/**
 * Client interface for communicating with the event-service via gRPC.
 */
public interface EventClient {

    EventResponse createEvent(EventCreateRequest request);

    EventResponse getEvent(String eventId);

    EventResponse updateEvent(EventUpdateRequest request);

    EventResponse publishEvent(String eventId);

    EventResponse cancelEvent(String eventId);

    SearchEventsResponse searchEvents(EventSearchRequest request);
}
