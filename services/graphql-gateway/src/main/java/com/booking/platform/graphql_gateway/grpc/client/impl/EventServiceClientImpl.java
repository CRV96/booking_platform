package com.booking.platform.graphql_gateway.grpc.client.impl;

import com.booking.platform.common.grpc.event.*;
import com.booking.platform.graphql_gateway.constants.EventServiceConst;
import com.booking.platform.graphql_gateway.dto.event.CreateEventInput;
import com.booking.platform.graphql_gateway.dto.event.EventSearchRequest;
import com.booking.platform.graphql_gateway.dto.event.UpdateEventInput;
import com.booking.platform.graphql_gateway.dto.event.VenueInput;
import com.booking.platform.graphql_gateway.grpc.client.EventClient;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EventServiceClientImpl implements EventClient {

    @GrpcClient(EventServiceConst.GRPC_CLIENT)
    private EventServiceGrpc.EventServiceBlockingStub eventServiceStub;

    @Override
    public EventResponse createEvent(CreateEventInput input) {
        log.debug("Calling event-service: CreateEvent title='{}'", input.title());

        CreateEventRequest.Builder requestBuilder = CreateEventRequest.newBuilder()
                .setTitle(input.title())
                .setCategory(input.category())
                .setDateTime(input.dateTime())
                .setVenue(buildVenue(input.venue()));

        if (input.description() != null) requestBuilder.setDescription(input.description());

        if (input.seatCategories() != null) {
            input.seatCategories().forEach(sc -> requestBuilder.addSeatCategories(
                    SeatCategoryInfo.newBuilder()
                            .setName(sc.name())
                            .setPrice(sc.price())
                            .setCurrency(sc.currency())
                            .setTotalSeats(sc.totalSeats())
                            .build()
            ));
        }

        return eventServiceStub.createEvent(requestBuilder.build());
    }

    @Override
    public EventResponse getEvent(String eventId) {
        log.debug("Calling event-service: GetEvent {}", eventId);

        return eventServiceStub.getEvent(
                GetEventRequest.newBuilder().setEventId(eventId).build()
        );
    }

    @Override
    public EventResponse updateEvent(String eventId, UpdateEventInput input) {
        log.debug("Calling event-service: UpdateEvent {}", eventId);

        UpdateEventRequest.Builder requestBuilder = UpdateEventRequest.newBuilder()
                .setEventId(eventId);

        if (input.title() != null) requestBuilder.setTitle(input.title());
        if (input.description() != null) requestBuilder.setDescription(input.description());
        if (input.category() != null) requestBuilder.setCategory(input.category());
        if (input.dateTime() != null) requestBuilder.setDateTime(input.dateTime());

        if (input.venue() != null) {
            requestBuilder.setVenue(buildVenue(input.venue()));
        }

        if (input.seatCategories() != null) {
            input.seatCategories().forEach(sc -> requestBuilder.addSeatCategories(
                    SeatCategoryInfo.newBuilder()
                            .setName(sc.name())
                            .setPrice(sc.price())
                            .setCurrency(sc.currency())
                            .setTotalSeats(sc.totalSeats())
                            .build()
            ));
        }

        return eventServiceStub.updateEvent(requestBuilder.build());
    }

    @Override
    public EventResponse publishEvent(String eventId) {
        log.debug("Calling event-service: PublishEvent {}", eventId);

        return eventServiceStub.publishEvent(
                PublishEventRequest.newBuilder().setEventId(eventId).build()
        );
    }

    @Override
    public EventResponse cancelEvent(String eventId) {
        log.debug("Calling event-service: CancelEvent {}", eventId);

        return eventServiceStub.cancelEvent(
                CancelEventRequest.newBuilder().setEventId(eventId).build()
        );
    }

    @Override
    public SearchEventsResponse searchEvents(EventSearchRequest request) {
        log.debug("Calling event-service: SearchEvents query='{}', category={}, city={}, page={}, size={}",
                request.query(), request.category(), request.city(), request.page(), request.pageSize());

        SearchEventsRequest.Builder requestBuilder = SearchEventsRequest.newBuilder()
                .setPage(request.page())
                .setPageSize(request.pageSize());

        if (request.query() != null) requestBuilder.setQuery(request.query());
        if (request.category() != null) requestBuilder.setCategory(request.category());
        if (request.city() != null) requestBuilder.setCity(request.city());
        if (request.dateFrom() != null) requestBuilder.setDateFrom(request.dateFrom());
        if (request.dateTo() != null) requestBuilder.setDateTo(request.dateTo());

        return eventServiceStub.searchEvents(requestBuilder.build());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private VenueInfo buildVenue(VenueInput venue) {
        VenueInfo.Builder builder = VenueInfo.newBuilder()
                .setName(venue.name())
                .setCity(venue.city())
                .setCountry(venue.country());

        if (venue.address() != null) builder.setAddress(venue.address());
        if (venue.latitude() != null) builder.setLatitude(venue.latitude());
        if (venue.longitude() != null) builder.setLongitude(venue.longitude());
        if (venue.capacity() != null) builder.setCapacity(venue.capacity());

        return builder.build();
    }
}
