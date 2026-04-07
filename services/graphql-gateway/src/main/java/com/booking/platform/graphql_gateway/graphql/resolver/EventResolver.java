package com.booking.platform.graphql_gateway.graphql.resolver;

import com.booking.platform.common.enums.Roles;
import com.booking.platform.graphql_gateway.dto.event.CreateEventInput;
import com.booking.platform.graphql_gateway.dto.event.Event;
import com.booking.platform.graphql_gateway.dto.event.EventConnection;
import com.booking.platform.graphql_gateway.dto.event.UpdateEventInput;
import com.booking.platform.graphql_gateway.grpc.client.EventClient;
import com.booking.platform.graphql_gateway.dto.event.EventSearchRequest;
import com.booking.platform.graphql_gateway.annotations.PublicEndpoint;
import com.booking.platform.graphql_gateway.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * GraphQL resolver for event queries and mutations.
 *
 * <p>Public endpoints (no authentication required):
 * <ul>
 *   <li>{@code event} — get a single event by ID</li>
 *   <li>{@code events} — search/list published events</li>
 * </ul>
 *
 * <p>Employee-only endpoints (requires "employee" role):
 * <ul>
 *   <li>{@code createEvent} — create a new event in DRAFT status</li>
 *   <li>{@code updateEvent} — update an existing event</li>
 *   <li>{@code publishEvent} — transition a DRAFT event to PUBLISHED</li>
 *   <li>{@code cancelEvent} — cancel a DRAFT or PUBLISHED event</li>
 * </ul>
 *
 * Exceptions are handled by {@link com.booking.platform.graphql_gateway.exception.GraphQLExceptionHandler}
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class EventResolver {

    private final EventClient eventClient;
    private final AuthService authService;

    // =========================================================================
    // QUERIES (public)
    // =========================================================================

    @PublicEndpoint
    @QueryMapping
    public Event event(@Argument("id") String id) {
        log.debug("GraphQL query: event({})", id);

        return Event.fromGrpc(eventClient.getEvent(id).getEvent());
    }

    @PublicEndpoint
    @QueryMapping
    public EventConnection events(
            @Argument("query") String query,
            @Argument("category") String category,
            @Argument("city") String city,
            @Argument("dateFrom") String dateFrom,
            @Argument("dateTo") String dateTo,
            @Argument("page") Integer page,
            @Argument("pageSize") Integer pageSize) {

        log.debug("GraphQL query: events(query={}, category={}, city={}, page={}, size={})",
                query, category, city, page, pageSize);

        var response = eventClient.searchEvents(new EventSearchRequest(
                query,
                category,
                city,
                dateFrom,
                dateTo,
                page != null ? page : 0,
                pageSize != null ? pageSize : 20
        ));

        List<Event> events = response.getEventsList().stream()
                .map(Event::fromGrpc)
                .toList();

        return new EventConnection(
                events,
                response.getPagination().getTotalCount(),
                response.getPagination().getPage(),
                response.getPagination().getPageSize(),
                response.getPagination().getTotalPages()
        );
    }

    // =========================================================================
    // MUTATIONS (employee only)
    // =========================================================================

    @MutationMapping
    public Event createEvent(@Argument("input") CreateEventInput input) {
        authService.requireRole(Roles.EMPLOYEE.getValue());
        log.info("GraphQL mutation: createEvent title='{}'", input.title());

        return Event.fromGrpc(eventClient.createEvent(input).getEvent());
    }

    @MutationMapping
    public Event updateEvent(
            @Argument("id") String id,
            @Argument("input") UpdateEventInput input) {
        authService.requireRole(Roles.EMPLOYEE.getValue());
        log.info("GraphQL mutation: updateEvent({})", id);

        return Event.fromGrpc(eventClient.updateEvent(id, input).getEvent());
    }

    @MutationMapping
    public Event publishEvent(@Argument("id") String id) {
        authService.requireRole(Roles.EMPLOYEE.getValue());
        log.info("GraphQL mutation: publishEvent({})", id);

        return Event.fromGrpc(eventClient.publishEvent(id).getEvent());
    }

    @MutationMapping
    public Event cancelEvent(@Argument("id") String id) {
        authService.requireRole(Roles.EMPLOYEE.getValue());
        log.info("GraphQL mutation: cancelEvent({})", id);

        return Event.fromGrpc(eventClient.cancelEvent(id).getEvent());
    }
}
