package com.booking.platform.booking_service.grpc.service;

import com.booking.platform.booking_service.entity.BookingEntity;
import com.booking.platform.booking_service.entity.enums.BookingStatus;
import com.booking.platform.booking_service.mapper.BookingMapper;
import com.booking.platform.booking_service.properties.BookingProperties;
import com.booking.platform.booking_service.service.BookingService;
import com.booking.platform.common.grpc.booking.*;
import com.booking.platform.common.grpc.context.GrpcUserContext;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

/**
 * gRPC service implementation for booking operations.
 * Delegates all business logic to {@link BookingService}.
 *
 * <p>All endpoints require an authenticated JWT — the user ID is extracted
 * from the gRPC context set by
 * {@link com.booking.platform.common.grpc.interceptor.JwtContextInterceptor}.</p>
 *
 * <p>Exception handling is delegated to
 * {@link com.booking.platform.common.grpc.interceptor.GrpcExceptionInterceptor}
 * which maps {@link com.booking.platform.common.exception.ServiceException} subclasses
 * to the appropriate gRPC status codes.</p>
 */
@GrpcService
@Slf4j
@RequiredArgsConstructor
public class BookingGrpcService extends BookingServiceGrpc.BookingServiceImplBase {

    private final BookingService bookingService;
    private final BookingMapper bookingMapper;
    private final BookingProperties bookingProperties;

    @Override
    public void createBooking(CreateBookingRequest request,
                              StreamObserver<BookingResponse> responseObserver) {
        String userId = requireUserId();

        log.debug("gRPC CreateBooking: user='{}', event='{}', category='{}', qty={}",
                userId, request.getEventId(), request.getSeatCategory(), request.getQuantity());

        validateCreateRequest(request);

        BookingEntity booking = bookingService.createBooking(
                userId,
                request.getEventId(),
                request.getSeatCategory(),
                request.getQuantity(),
                request.getIdempotencyKey()
        );

        log.info("gRPC CreateBooking completed: bookingId='{}', event='{}'",
                booking.getId(), request.getEventId());
        responseObserver.onNext(buildBookingResponse(booking));
        responseObserver.onCompleted();
    }

    @Override
    public void getBooking(GetBookingRequest request,
                           StreamObserver<BookingResponse> responseObserver) {
        String userId = requireUserId();

        log.debug("gRPC GetBooking: user='{}', bookingId='{}'", userId, request.getBookingId());

        UUID bookingId = parseUuid(request.getBookingId(), "booking_id");

        BookingEntity booking = bookingService.getBooking(bookingId, userId);

        responseObserver.onNext(buildBookingResponse(booking));
        responseObserver.onCompleted();
    }

    @Override
    public void getUserBookings(GetUserBookingsRequest request,
                                StreamObserver<GetUserBookingsResponse> responseObserver) {
        String userId = requireUserId();

        log.debug("gRPC GetUserBookings: user='{}', page={}, pageSize={}, status='{}'",
                userId, request.getPage(), request.getPageSize(),
                request.hasStatusFilter() ? request.getStatusFilter() : "ALL");

        int page = Math.max(request.getPage(), 0);
        BookingProperties.Pagination pagination = bookingProperties.getPagination();
        int pageSize = request.getPageSize() > 0
                ? Math.min(request.getPageSize(), pagination.getMaxPageSize())
                : pagination.getDefaultPageSize();
        String statusFilter = request.hasStatusFilter() ? request.getStatusFilter() : null;

        Page<BookingEntity> bookingPage = bookingService.getUserBookings(
                userId, page, pageSize, statusFilter);

        GetUserBookingsResponse response = GetUserBookingsResponse.newBuilder()
                .addAllBookings(bookingMapper.toProtoList(bookingPage.getContent()))
                .setPagination(PaginationInfo.newBuilder()
                        .setTotalCount((int) bookingPage.getTotalElements())
                        .setPage(page)
                        .setPageSize(pageSize)
                        .setTotalPages(bookingPage.getTotalPages())
                        .build())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void cancelBooking(CancelBookingRequest request,
                              StreamObserver<BookingResponse> responseObserver) {
        String userId = requireUserId();

        log.debug("gRPC CancelBooking: user='{}', bookingId='{}'", userId, request.getBookingId());

        UUID bookingId = parseUuid(request.getBookingId(), "booking_id");
        String reason = request.getReason().isBlank() ? null : request.getReason();

        BookingEntity booking = bookingService.cancelBooking(bookingId, userId, reason);

        log.info("gRPC CancelBooking completed: bookingId='{}', reason='{}'",
                bookingId, reason);
        responseObserver.onNext(buildBookingResponse(booking));
        responseObserver.onCompleted();
    }

    @Override
    public void getBookingAttendees(GetBookingAttendeesRequest request, StreamObserver<GetBookingAttendeesResponse> responseObserver) {
        log.debug("gRPC GetBookingAttendees for the eventId='{}'", request.getEventId());

        final List<String> attendees = bookingService.getAttendeeIdsForEvent(request.getEventId(), BookingStatus.valueOf(request.getEventStatus()));

        log.debug("Fetched {} attendees for eventId='{}'", attendees.size(), request.getEventId());

        responseObserver.onNext(GetBookingAttendeesResponse.newBuilder().addAllAttendees(attendees).build());
        responseObserver.onCompleted();
    }

    private BookingResponse buildBookingResponse(BookingEntity booking) {
        return BookingResponse.newBuilder()
                .setBooking(bookingMapper.toProto(booking))
                .build();
    }

    /**
     * Extracts and validates the authenticated user ID from the gRPC context.
     * All booking endpoints require authentication.
     */
    private String requireUserId() {
        String userId = GrpcUserContext.getUserId();
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Authenticated user ID is required");
        }
        return userId;
    }

    /**
     * Validates the create booking request fields.
     * {@link IllegalArgumentException} → mapped to INVALID_ARGUMENT by GrpcExceptionInterceptor.
     */
    private void validateCreateRequest(CreateBookingRequest request) {
        if (request.getEventId().isBlank()) {
            throw new IllegalArgumentException("event_id is required");
        }
        if (request.getSeatCategory().isBlank()) {
            throw new IllegalArgumentException("seat_category is required");
        }
        if (request.getQuantity() <= 0) {
            throw new IllegalArgumentException("quantity must be greater than 0");
        }
        if (request.getIdempotencyKey().isBlank()) {
            throw new IllegalArgumentException("idempotency_key is required");
        }
    }

    private UUID parseUuid(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(fieldName + " must be a valid UUID: " + value);
        }
    }
}
