package com.booking.platform.graphql_gateway.graphql.resolver;

import com.booking.platform.common.logging.ApplicationLogger;
import com.booking.platform.graphql_gateway.dto.event.Event;
import com.booking.platform.graphql_gateway.dto.lovelist.LovelistItem;
import com.booking.platform.graphql_gateway.grpc.client.EventClient;
import com.booking.platform.graphql_gateway.grpc.client.LovelistClient;
import com.booking.platform.graphql_gateway.service.AuthService;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * GraphQL resolver for the per-user lovelist (favorites).
 *
 * <p>All operations require authentication — the user is resolved from the JWT and
 * forwarded to booking-service. The {@code event} field on each entry is hydrated live
 * from event-service, and only when the query asks for it.</p>
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class LovelistResolver {

    private final LovelistClient lovelistClient;
    private final EventClient eventClient;
    private final AuthService authService;

    // ── Queries ───────────────────────────────────────────────────────────────

    @QueryMapping
    public List<LovelistItem> lovelist() {
        String userId = authService.getAuthenticatedUserId();
        ApplicationLogger.logMessage(log, Level.DEBUG, "GraphQL query: lovelist for user '{}'", userId);
        return toDtos(lovelistClient.getLoveList());
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    @MutationMapping
    public List<LovelistItem> addFavorite(@Argument("eventId") String eventId) {
        String userId = authService.getAuthenticatedUserId();
        ApplicationLogger.logMessage(log, Level.INFO, "GraphQL mutation: addFavorite({}) for user '{}'", eventId, userId);
        return toDtos(lovelistClient.addFavorite(eventId));
    }

    @MutationMapping
    public List<LovelistItem> removeFavorite(@Argument("eventId") String eventId) {
        String userId = authService.getAuthenticatedUserId();
        ApplicationLogger.logMessage(log, Level.INFO, "GraphQL mutation: removeFavorite({}) for user '{}'", eventId, userId);
        return toDtos(lovelistClient.removeFavorite(eventId));
    }

    // ── Field hydration ─────────────────────────────────────────────────────────

    /** Hydrates live event details for a lovelist entry, or null if the event no longer exists. */
    @SchemaMapping(typeName = "LovelistItem", field = "event")
    public Event event(LovelistItem item) {
        try {
            return Event.fromGrpc(eventClient.getEvent(item.eventId()).getEvent());
        } catch (StatusRuntimeException e) {
            ApplicationLogger.logMessage(log, Level.DEBUG,
                    "Lovelist hydration skipped for event='{}': {}", item.eventId(), e.getStatus().getCode());
            return null;
        }
    }

    private List<LovelistItem> toDtos(com.booking.platform.common.grpc.booking.LoveListResponse response) {
        return response.getItemsList().stream()
                .map(LovelistItem::fromGrpc)
                .toList();
    }
}
