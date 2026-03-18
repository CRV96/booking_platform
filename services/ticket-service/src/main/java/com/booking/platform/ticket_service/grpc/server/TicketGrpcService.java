package com.booking.platform.ticket_service.grpc.server;

import com.booking.platform.common.enums.Roles;
import com.booking.platform.common.grpc.context.GrpcUserContext;
import com.booking.platform.common.grpc.ticket.*;
import com.booking.platform.ticket_service.document.TicketDocument;
import com.booking.platform.common.exceptions.PermissionDeniedException;
import com.booking.platform.ticket_service.mapper.TicketProtoMapper;
import com.booking.platform.ticket_service.service.TicketService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

/**
 * gRPC service implementation for ticket operations.
 * Delegates all business logic to {@link TicketService}.
 *
 * <p>Customer endpoint: GetMyTickets (userId extracted from JWT — customer can only see their own tickets)
 * <p>Employee endpoints: GetTicketsByBooking, GetTicketsByUser, GetTicketByNumber, ValidateTicket, CancelTicket
 *
 * <p>Exception handling is delegated to
 * {@link com.booking.platform.common.grpc.interceptor.GrpcExceptionInterceptor}
 * which maps {@link com.booking.platform.common.exception.ServiceException} subclasses
 * to the appropriate gRPC status codes.
 */
@GrpcService
@Slf4j
@RequiredArgsConstructor
public class TicketGrpcService extends TicketServiceGrpc.TicketServiceImplBase {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final TicketService ticketService;
    private final TicketProtoMapper ticketProtoMapper;

    // =========================================================================
    // CUSTOMER ENDPOINT
    // =========================================================================

    @Override
    public void getMyTickets(GetMyTicketsRequest request,
                             StreamObserver<GetMyTicketsResponse> responseObserver) {
        String userId = requireUserId();

        log.debug("gRPC GetMyTickets: user='{}'", userId);

        int page = Math.max(request.getPage(), 0);
        int pageSize = normalizePageSize(request.getPageSize());

        Page<TicketDocument> ticketPage = ticketService.getTicketsByUserId(userId, PageRequest.of(page, pageSize));

        GetMyTicketsResponse response = GetMyTicketsResponse.newBuilder()
                .addAllTickets(ticketProtoMapper.toProtoList(ticketPage.getContent()))
                .setPagination(buildPagination(ticketPage, page, pageSize))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    // =========================================================================
    // EMPLOYEE ENDPOINTS
    // =========================================================================

    @Override
    public void getTicketsByBooking(GetTicketsByBookingRequest request,
                                    StreamObserver<GetTicketsByBookingResponse> responseObserver) {
        requireRole(Roles.EMPLOYEE);

        log.debug("gRPC GetTicketsByBooking: bookingId='{}'", request.getBookingId());

        List<TicketDocument> tickets = ticketService.getTicketsByBooking(request.getBookingId());

        GetTicketsByBookingResponse response = GetTicketsByBookingResponse.newBuilder()
                .addAllTickets(ticketProtoMapper.toProtoList(tickets))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getTicketsByUser(GetTicketsByUserRequest request,
                                 StreamObserver<GetTicketsByUserResponse> responseObserver) {
        requireRole(Roles.EMPLOYEE);

        log.debug("gRPC GetTicketsByUser: userId='{}'", request.getUserId());

        int page = Math.max(request.getPage(), 0);
        int pageSize = normalizePageSize(request.getPageSize());

        Page<TicketDocument> ticketPage = ticketService.getTicketsByUserId(request.getUserId(), PageRequest.of(page, pageSize));

        GetTicketsByUserResponse response = GetTicketsByUserResponse.newBuilder()
                .addAllTickets(ticketProtoMapper.toProtoList(ticketPage.getContent()))
                .setPagination(buildPagination(ticketPage, page, pageSize))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getTicketByNumber(GetTicketByNumberRequest request,
                                  StreamObserver<TicketResponse> responseObserver) {
        requireRole(Roles.EMPLOYEE);

        log.debug("gRPC GetTicketByNumber: ticketNumber='{}'", request.getTicketNumber());

        TicketDocument ticket = ticketService.getByTicketNumber(request.getTicketNumber());

        responseObserver.onNext(buildTicketResponse(ticket));
        responseObserver.onCompleted();
    }

    @Override
    public void validateTicket(ValidateTicketRequest request,
                               StreamObserver<TicketResponse> responseObserver) {
        requireRole(Roles.EMPLOYEE);

        log.debug("gRPC ValidateTicket: ticketNumber='{}'", request.getTicketNumber());

        TicketDocument ticket = ticketService.validateTicket(request.getTicketNumber());

        responseObserver.onNext(buildTicketResponse(ticket));
        responseObserver.onCompleted();

        log.info("gRPC ValidateTicket completed: ticket '{}' marked as USED", request.getTicketNumber());
    }

    @Override
    public void cancelTicket(CancelTicketRequest request,
                             StreamObserver<TicketResponse> responseObserver) {
        requireRole(Roles.EMPLOYEE);

        log.debug("gRPC CancelTicket: ticketNumber='{}'", request.getTicketNumber());

        TicketDocument ticket = ticketService.cancelTicket(request.getTicketNumber());

        responseObserver.onNext(buildTicketResponse(ticket));
        responseObserver.onCompleted();

        log.info("gRPC CancelTicket completed: ticket '{}' status='{}'",
                request.getTicketNumber(), ticket.getStatus());
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private TicketResponse buildTicketResponse(TicketDocument ticket) {
        return TicketResponse.newBuilder()
                .setTicket(ticketProtoMapper.toProto(ticket))
                .build();
    }

    private PaginationInfo buildPagination(Page<?> page, int pageNumber, int pageSize) {
        return PaginationInfo.newBuilder()
                .setTotalCount((int) page.getTotalElements())
                .setPage(pageNumber)
                .setPageSize(pageSize)
                .setTotalPages(page.getTotalPages())
                .build();
    }

    private int normalizePageSize(int requested) {
        if (requested <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    /**
     * Extracts and validates the authenticated user ID from the gRPC context.
     */
    private String requireUserId() {
        String userId = GrpcUserContext.getUserId();
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Authenticated user ID is required");
        }
        return userId;
    }

    /**
     * Enforces that the authenticated user has the required role.
     * Throws {@link PermissionDeniedException} (→ gRPC PERMISSION_DENIED) if not.
     */
    private void requireRole(Roles role) {
        if (!GrpcUserContext.hasRole(role.getValue())) {
            log.warn("Access denied for user '{}': missing role '{}'",
                    GrpcUserContext.getUserId(), role);
            throw new PermissionDeniedException(
                    "Access denied: role '" + role + "' is required");
        }
    }
}
