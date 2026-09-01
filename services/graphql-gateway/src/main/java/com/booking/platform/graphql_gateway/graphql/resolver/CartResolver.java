package com.booking.platform.graphql_gateway.graphql.resolver;

import com.booking.platform.common.logging.ApplicationLogger;
import com.booking.platform.graphql_gateway.dto.cart.AddToCartInput;
import com.booking.platform.graphql_gateway.dto.cart.Cart;
import com.booking.platform.graphql_gateway.dto.cart.CartItem;
import com.booking.platform.graphql_gateway.dto.event.Event;
import com.booking.platform.graphql_gateway.grpc.client.CartClient;
import com.booking.platform.graphql_gateway.grpc.client.EventClient;
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

/**
 * GraphQL resolver for the per-user cart.
 *
 * <p>All operations require authentication — the user is resolved from the JWT and
 * forwarded to booking-service, which owns the data. The {@code event} field on each
 * line is hydrated live from event-service, and only when the query asks for it.</p>
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class CartResolver {

    private final CartClient cartClient;
    private final EventClient eventClient;
    private final AuthService authService;

    // ── Queries ───────────────────────────────────────────────────────────────

    @QueryMapping
    public Cart cart() {
        String userId = authService.getAuthenticatedUserId();
        ApplicationLogger.logMessage(log, Level.DEBUG, "GraphQL query: cart for user '{}'", userId);
        return Cart.fromGrpc(cartClient.getCart());
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    @MutationMapping
    public Cart addToCart(@Argument("input") AddToCartInput input) {
        String userId = authService.getAuthenticatedUserId();
        ApplicationLogger.logMessage(log, Level.INFO,
                "GraphQL mutation: addToCart event='{}', category='{}', qty={} for user '{}'",
                input.eventId(), input.seatCategory(), input.quantity(), userId);
        return Cart.fromGrpc(cartClient.addToCart(input));
    }

    @MutationMapping
    public Cart updateCartItem(@Argument("cartItemId") String cartItemId, @Argument("quantity") int quantity) {
        String userId = authService.getAuthenticatedUserId();
        ApplicationLogger.logMessage(log, Level.INFO,
                "GraphQL mutation: updateCartItem({}, qty={}) for user '{}'", cartItemId, quantity, userId);
        return Cart.fromGrpc(cartClient.updateCartItem(cartItemId, quantity));
    }

    @MutationMapping
    public Cart removeFromCart(@Argument("cartItemId") String cartItemId) {
        String userId = authService.getAuthenticatedUserId();
        ApplicationLogger.logMessage(log, Level.INFO,
                "GraphQL mutation: removeFromCart({}) for user '{}'", cartItemId, userId);
        return Cart.fromGrpc(cartClient.removeFromCart(cartItemId));
    }

    @MutationMapping
    public Cart clearCart() {
        String userId = authService.getAuthenticatedUserId();
        ApplicationLogger.logMessage(log, Level.INFO, "GraphQL mutation: clearCart for user '{}'", userId);
        return Cart.fromGrpc(cartClient.clearCart());
    }

    // ── Field hydration ─────────────────────────────────────────────────────────

    /** Hydrates live event details for a cart line, or null if the event no longer exists. */
    @SchemaMapping(typeName = "CartItem", field = "event")
    public Event event(CartItem item) {
        return hydrateEvent(item.eventId());
    }

    private Event hydrateEvent(String eventId) {
        try {
            return Event.fromGrpc(eventClient.getEvent(eventId).getEvent());
        } catch (StatusRuntimeException e) {
            ApplicationLogger.logMessage(log, Level.DEBUG,
                    "Cart hydration skipped for event='{}': {}", eventId, e.getStatus().getCode());
            return null;
        }
    }
}
