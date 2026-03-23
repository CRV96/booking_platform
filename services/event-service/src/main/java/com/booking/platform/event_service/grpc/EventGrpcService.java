package com.booking.platform.event_service.grpc;

import com.booking.platform.common.enums.Roles;
import com.booking.platform.common.grpc.context.GrpcUserContext;
import com.booking.platform.common.grpc.event.*;
import com.booking.platform.common.security.PublicEndpoint;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.dto.OrganizerDto;
import com.booking.platform.event_service.exception.PermissionDeniedException;
import com.booking.platform.event_service.mapper.EventMapper;
import com.booking.platform.event_service.properties.EventProperties;
import com.booking.platform.event_service.service.EventService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;

/**
 * gRPC service implementation for event operations.
 * Delegates all business logic to {@link EventService}.
 *
 * Public endpoints (no JWT required): GetEvent, SearchEvents
 * Protected endpoints (employee role required): CreateEvent, UpdateEvent, PublishEvent, CancelEvent
 * Internal endpoint (service-to-service): UpdateSeatAvailability
 *
 * Exception handling is delegated to {@link com.booking.platform.common.grpc.interceptor.GrpcExceptionInterceptor}
 */
@GrpcService
@Slf4j
@RequiredArgsConstructor
public class EventGrpcService extends EventServiceGrpc.EventServiceImplBase {

    private final EventService eventService;
    private final EventMapper eventMapper;
    private final EventProperties eventProperties;

    // =========================================================================
    // PUBLIC ENDPOINTS
    // =========================================================================
    @PublicEndpoint
    @Override
    public void getEvent(GetEventRequest request, StreamObserver<EventResponse> responseObserver) {
        log.debug("gRPC GetEvent: id='{}'", request.getEventId());

        EventDocument event = eventService.getEvent(request.getEventId());

        responseObserver.onNext(buildEventResponse(event));
        responseObserver.onCompleted();
    }

    @PublicEndpoint
    @Override
    public void searchEvents(SearchEventsRequest request, StreamObserver<SearchEventsResponse> responseObserver) {
        log.debug("gRPC SearchEvents: query='{}', category='{}', city='{}', page={}",
                request.getQuery(), request.getCategory(), request.getCity(), request.getPage());

        List<EventDocument> events = eventService.searchEvents(request);

        int page = Math.max(request.getPage(), 0);
        int pageSize = normalizePageSize(request.getPageSize());
        int totalCount = events.size();
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalCount / pageSize) : 0;

        SearchEventsResponse response = SearchEventsResponse.newBuilder()
                .addAllEvents(eventMapper.toProtoList(events))
                .setPagination(PaginationInfo.newBuilder()
                        .setTotalCount(totalCount)
                        .setPage(page)
                        .setPageSize(pageSize)
                        .setTotalPages(totalPages)
                        .build())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    // =========================================================================
    // EMPLOYEE-ONLY ENDPOINTS
    // =========================================================================
    @Override
    public void createEvent(CreateEventRequest request, StreamObserver<EventResponse> responseObserver) {
        log.debug("gRPC CreateEvent: title='{}'", request.getTitle());

        requireRole(Roles.EMPLOYEE.getValue());

        OrganizerDto organizer = OrganizerDto.builder()
                .userId(GrpcUserContext.getUserId())
                .name(GrpcUserContext.getUsername())
                .email(GrpcUserContext.getEmail())
                .build();

        EventDocument event = eventService.createEvent(request, organizer);
        log.info("gRPC CreateEvent completed: id='{}', title='{}'", event.getId(), event.getTitle());

        responseObserver.onNext(buildEventResponse(event));
        responseObserver.onCompleted();
    }

    @Override
    public void updateEvent(UpdateEventRequest request, StreamObserver<EventResponse> responseObserver) {
        log.debug("gRPC UpdateEvent: id='{}'", request.getEventId());

        requireRole(Roles.EMPLOYEE.getValue());
        requireOwnership(request.getEventId());

        EventDocument event = eventService.updateEvent(request.getEventId(), request);
        log.info("gRPC UpdateEvent completed: id='{}'", event.getId());

        responseObserver.onNext(buildEventResponse(event));
        responseObserver.onCompleted();
    }

    @Override
    public void publishEvent(PublishEventRequest request, StreamObserver<EventResponse> responseObserver) {
        log.debug("gRPC PublishEvent: id='{}'", request.getEventId());

        requireRole(Roles.EMPLOYEE.getValue());

        EventDocument event = eventService.publishEvent(request.getEventId());
        log.info("gRPC PublishEvent completed: id='{}', title='{}'", event.getId(), event.getTitle());

        responseObserver.onNext(buildEventResponse(event));
        responseObserver.onCompleted();
    }

    @Override
    public void cancelEvent(CancelEventRequest request, StreamObserver<EventResponse> responseObserver) {
        log.debug("gRPC CancelEvent: id='{}'", request.getEventId());

        requireRole(Roles.EMPLOYEE.getValue());
        requireOwnership(request.getEventId());

        String reason = request.getReason().isEmpty() ? null : request.getReason();
        EventDocument event = eventService.cancelEvent(request.getEventId(), reason);
        log.info("gRPC CancelEvent completed: id='{}', reason='{}'", event.getId(), reason);

        responseObserver.onNext(buildEventResponse(event));
        responseObserver.onCompleted();
    }

    // =========================================================================
    // INTERNAL — service-to-service only (booking-service)
    // =========================================================================
    @Override
    public void updateSeatAvailability(UpdateSeatAvailabilityRequest request,
                                       StreamObserver<UpdateSeatAvailabilityResponse> responseObserver) {
        log.debug("gRPC UpdateSeatAvailability: event='{}', category='{}', delta={}",
                request.getEventId(), request.getSeatCategoryName(), request.getDelta());

        EventDocument event = eventService.updateSeatAvailability(
                request.getEventId(),
                request.getSeatCategoryName(),
                request.getDelta()
        );

        int remainingSeats = event.getSeatCategories() == null ? 0
                : event.getSeatCategories().stream()
                        .filter(sc -> sc.getName().equals(request.getSeatCategoryName()))
                        .mapToInt(sc -> sc.getAvailableSeats() != null ? sc.getAvailableSeats() : 0)
                        .findFirst()
                        .orElse(0);

        UpdateSeatAvailabilityResponse response = UpdateSeatAvailabilityResponse.newBuilder()
                .setSuccess(true)
                .setRemainingSeats(remainingSeats)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    // =========================================================================
    // HELPERS
    // =========================================================================
    private int normalizePageSize(int requested) {
        if (requested <= 0) return eventProperties.pagination().defaultPageSize();
        return Math.min(requested, eventProperties.pagination().maxPageSize());
    }

    private EventResponse buildEventResponse(EventDocument event) {
        return EventResponse.newBuilder()
                .setEvent(eventMapper.toProto(event))
                .build();
    }

    /**
     * Enforces that the authenticated user has the required role.
     * Throws {@link PermissionDeniedException} (→ gRPC PERMISSION_DENIED) if not.
     */
    private void requireRole(String role) {
        if (!GrpcUserContext.hasRole(role)) {
            log.warn("Access denied for user '{}': missing role '{}'",
                    GrpcUserContext.getUserId(), role);
            throw new PermissionDeniedException(
                    "Access denied: role '" + role + "' is required");
        }
    }

    /**
     * Enforces that the authenticated user is the organizer of the given event.
     * Throws {@link PermissionDeniedException} if the user is not the event owner.
     */
    private void requireOwnership(String eventId) {
        EventDocument event = eventService.getEvent(eventId);
        String userId = GrpcUserContext.getUserId();
        if (event.getOrganizer() == null || !userId.equals(event.getOrganizer().getUserId())) {
            log.warn("Ownership denied for user '{}' on event '{}'", userId, eventId);
            throw new PermissionDeniedException(
                    "Access denied: only the event organizer can perform this operation");
        }
    }

}
