package com.booking.platform.graphql_gateway.graphql.resolver;

import com.booking.platform.common.enums.Roles;
import com.booking.platform.graphql_gateway.dto.ticket.Ticket;
import com.booking.platform.graphql_gateway.dto.ticket.TicketConnection;
import com.booking.platform.graphql_gateway.grpc.client.TicketClient;
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
 * GraphQL resolver for ticket queries and mutations.
 *
 * <p>Customer endpoint (any authenticated user):
 * <ul>
 *   <li>{@code myTickets} — list the authenticated user's own tickets</li>
 * </ul>
 *
 * <p>Employee-only endpoints (requires "employee" role):
 * <ul>
 *   <li>{@code ticketsByBooking} — get all tickets for a specific booking</li>
 *   <li>{@code ticketsByUser} — get all tickets for a specific user</li>
 *   <li>{@code ticket} — get a single ticket by ticket number</li>
 *   <li>{@code validateTicket} — mark a ticket as USED (venue scan)</li>
 *   <li>{@code cancelTicket} — mark a ticket as CANCELLED (refund)</li>
 * </ul>
 *
 * Exceptions are handled by {@link com.booking.platform.graphql_gateway.exception.GraphQLExceptionHandler}
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class TicketResolver {

    private final TicketClient ticketClient;
    private final AuthService authService;

    // =========================================================================
    // QUERIES (customer)
    // =========================================================================

    @QueryMapping
    public TicketConnection myTickets(
            @Argument("page") Integer page,
            @Argument("pageSize") Integer pageSize) {

        String userId = authService.getAuthenticatedUserId();
        ApplicationLogger.logMessage(log, Level.DEBUG, "GraphQL query: myTickets(page={}, size={}) for user '{}'", page, pageSize, userId);

        int actualPage = page != null ? page : 0;
        int actualPageSize = pageSize != null ? pageSize : 20;

        var response = ticketClient.getMyTickets(actualPage, actualPageSize);

        List<Ticket> tickets = response.getTicketsList().stream()
                .map(Ticket::fromGrpc)
                .toList();

        return new TicketConnection(
                tickets,
                response.getPagination().getTotalCount(),
                response.getPagination().getPage(),
                response.getPagination().getPageSize(),
                response.getPagination().getTotalPages()
        );
    }

    // =========================================================================
    // QUERIES (employee only)
    // =========================================================================

    @QueryMapping
    public List<Ticket> ticketsByBooking(@Argument("bookingId") String bookingId) {
        authService.requireRole(Roles.EMPLOYEE.getValue());
        ApplicationLogger.logMessage(log, Level.DEBUG, "GraphQL query: ticketsByBooking({})", bookingId);

        return ticketClient.getTicketsByBooking(bookingId).getTicketsList().stream()
                .map(Ticket::fromGrpc)
                .toList();
    }

    @QueryMapping
    public TicketConnection ticketsByUser(
            @Argument("userId") String userId,
            @Argument("page") Integer page,
            @Argument("pageSize") Integer pageSize) {

        authService.requireRole(Roles.EMPLOYEE.getValue());
        ApplicationLogger.logMessage(log, Level.DEBUG, "GraphQL query: ticketsByUser(userId={}, page={}, size={})", userId, page, pageSize);

        int actualPage = page != null ? page : 0;
        int actualPageSize = pageSize != null ? pageSize : 20;

        var response = ticketClient.getTicketsByUser(userId, actualPage, actualPageSize);

        List<Ticket> tickets = response.getTicketsList().stream()
                .map(Ticket::fromGrpc)
                .toList();

        return new TicketConnection(
                tickets,
                response.getPagination().getTotalCount(),
                response.getPagination().getPage(),
                response.getPagination().getPageSize(),
                response.getPagination().getTotalPages()
        );
    }

    @QueryMapping
    public Ticket ticket(@Argument("ticketNumber") String ticketNumber) {
        authService.requireRole(Roles.EMPLOYEE.getValue());
        ApplicationLogger.logMessage(log, Level.DEBUG, "GraphQL query: ticket({})", ticketNumber);

        return Ticket.fromGrpc(ticketClient.getTicketByNumber(ticketNumber).getTicket());
    }

    // =========================================================================
    // MUTATIONS (employee only)
    // =========================================================================

    @MutationMapping
    public Ticket validateTicket(@Argument("ticketNumber") String ticketNumber) {
        authService.requireRole(Roles.EMPLOYEE.getValue());
        ApplicationLogger.logMessage(log, Level.INFO, "GraphQL mutation: validateTicket({})", ticketNumber);

        return Ticket.fromGrpc(ticketClient.validateTicket(ticketNumber).getTicket());
    }

    @MutationMapping
    public Ticket cancelTicket(@Argument("ticketNumber") String ticketNumber) {
        authService.requireRole(Roles.EMPLOYEE.getValue());
        ApplicationLogger.logMessage(log, Level.INFO, "GraphQL mutation: cancelTicket({})", ticketNumber);

        return Ticket.fromGrpc(ticketClient.cancelTicket(ticketNumber).getTicket());
    }
}
