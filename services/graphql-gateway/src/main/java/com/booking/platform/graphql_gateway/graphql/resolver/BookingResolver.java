package com.booking.platform.graphql_gateway.graphql.resolver;

import com.booking.platform.graphql_gateway.dto.booking.Booking;
import com.booking.platform.graphql_gateway.dto.booking.BookingConnection;
import com.booking.platform.graphql_gateway.dto.booking.CreateBookingInput;
import com.booking.platform.graphql_gateway.grpc.client.BookingClient;
import com.booking.platform.graphql_gateway.service.AuthService;
import com.booking.platform.common.logging.ApplicationLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * GraphQL resolver for booking queries and mutations.
 *
 * <p>All endpoints require authentication — the user ID is extracted from the
 * JWT token and forwarded to booking-service via {@code JwtForwardingClientInterceptor}.
 * Booking-service enforces ownership (users can only access their own bookings).</p>
 *
 * <p>Exceptions from booking-service (gRPC StatusRuntimeException) are handled by
 * {@link com.booking.platform.graphql_gateway.exception.GraphQLExceptionHandler}.</p>
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class BookingResolver {

    private final BookingClient bookingClient;
    private final AuthService authService;

    // =========================================================================
    // QUERIES
    // =========================================================================

    @QueryMapping
    public Booking booking(@Argument("id") String id) {
        String userId = authService.getAuthenticatedUserId();
        ApplicationLogger.logMessage(log, Level.DEBUG, "GraphQL query: booking({}) for user '{}'", id, userId);

        return Booking.fromGrpc(bookingClient.getBooking(id).getBooking());
    }

    @QueryMapping
    public BookingConnection myBookings(
            @Argument("page") Integer page,
            @Argument("pageSize") Integer pageSize,
            @Argument("status") String status) {

        String userId = authService.getAuthenticatedUserId();
        ApplicationLogger.logMessage(log, Level.DEBUG, "GraphQL query: myBookings(page={}, size={}, status='{}') for user '{}'",
                page, pageSize, status, userId);

        int actualPage = page != null ? page : 0;
        int actualPageSize = pageSize != null ? pageSize : 20;

        var response = bookingClient.getUserBookings(userId, actualPage, actualPageSize, status);

        List<Booking> bookings = response.getBookingsList().stream()
                .map(Booking::fromGrpc)
                .toList();

        return new BookingConnection(
                bookings,
                response.getPagination().getTotalCount(),
                response.getPagination().getPage(),
                response.getPagination().getPageSize(),
                response.getPagination().getTotalPages()
        );
    }

    // =========================================================================
    // MUTATIONS
    // =========================================================================

    @MutationMapping
    public Booking createBooking(@Argument("input") CreateBookingInput input) {
        String userId = authService.getAuthenticatedUserId();
        ApplicationLogger.logMessage(log, Level.INFO, "GraphQL mutation: createBooking event='{}', category='{}', qty={} for user '{}'",
                input.eventId(), input.seatCategory(), input.quantity(), userId);

        return Booking.fromGrpc(bookingClient.createBooking(
                input.eventId(),
                input.seatCategory(),
                input.quantity(),
                input.idempotencyKey()
        ).getBooking());
    }

    @MutationMapping
    public Booking cancelBooking(
            @Argument("id") String id,
            @Argument("reason") String reason) {

        String userId = authService.getAuthenticatedUserId();
        ApplicationLogger.logMessage(log, Level.INFO, "GraphQL mutation: cancelBooking({}, reason='{}') for user '{}'", id, reason, userId);

        return Booking.fromGrpc(bookingClient.cancelBooking(id, reason).getBooking());
    }
}
