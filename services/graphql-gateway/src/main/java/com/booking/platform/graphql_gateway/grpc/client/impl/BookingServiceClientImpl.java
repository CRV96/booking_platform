package com.booking.platform.graphql_gateway.grpc.client.impl;

import com.booking.platform.common.grpc.booking.*;
import com.booking.platform.graphql_gateway.constants.BookingServiceConst;
import com.booking.platform.graphql_gateway.grpc.client.BookingClient;
import com.booking.platform.common.logging.ApplicationLogger;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

/**
 * gRPC client implementation for calling booking-service.
 * JWT is forwarded automatically by {@code JwtForwardingClientInterceptor}.
 */
@Service
@Slf4j
public class BookingServiceClientImpl implements BookingClient {

    @GrpcClient(BookingServiceConst.GRPC_CLIENT)
    private BookingServiceGrpc.BookingServiceBlockingStub bookingServiceStub;

    @Override
    public BookingResponse createBooking(String eventId, String seatCategory,
                                         int quantity, String idempotencyKey) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling booking-service: CreateBooking event='{}', category='{}', qty={}",
                eventId, seatCategory, quantity);

        return bookingServiceStub.createBooking(
                CreateBookingRequest.newBuilder()
                        .setEventId(eventId)
                        .setSeatCategory(seatCategory)
                        .setQuantity(quantity)
                        .setIdempotencyKey(idempotencyKey)
                        .build()
        );
    }

    @Override
    public BookingResponse getBooking(String bookingId) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling booking-service: GetBooking id='{}'", bookingId);

        return bookingServiceStub.getBooking(
                GetBookingRequest.newBuilder()
                        .setBookingId(bookingId)
                        .build()
        );
    }

    @Override
    public GetUserBookingsResponse getUserBookings(String userId, int page,
                                                    int pageSize, String statusFilter) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling booking-service: GetUserBookings user='{}', page={}, size={}, status='{}'",
                userId, page, pageSize, statusFilter);

        GetUserBookingsRequest.Builder builder = GetUserBookingsRequest.newBuilder()
                .setUserId(userId)
                .setPage(page)
                .setPageSize(pageSize);

        if (statusFilter != null) {
            builder.setStatusFilter(statusFilter);
        }

        return bookingServiceStub.getUserBookings(builder.build());
    }

    @Override
    public BookingResponse cancelBooking(String bookingId, String reason) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling booking-service: CancelBooking id='{}', reason='{}'", bookingId, reason);

        CancelBookingRequest.Builder builder = CancelBookingRequest.newBuilder()
                .setBookingId(bookingId);

        if (reason != null) {
            builder.setReason(reason);
        }

        return bookingServiceStub.cancelBooking(builder.build());
    }
}
