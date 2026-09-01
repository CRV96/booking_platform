package com.booking.platform.graphql_gateway.graphql.resolver;

import com.booking.platform.common.grpc.booking.CartResponse;
import com.booking.platform.common.grpc.event.EventInfo;
import com.booking.platform.common.grpc.event.EventResponse;
import com.booking.platform.graphql_gateway.dto.cart.AddToCartInput;
import com.booking.platform.graphql_gateway.dto.cart.Cart;
import com.booking.platform.graphql_gateway.dto.cart.CartItem;
import com.booking.platform.graphql_gateway.dto.event.Event;
import com.booking.platform.graphql_gateway.grpc.client.CartClient;
import com.booking.platform.graphql_gateway.grpc.client.EventClient;
import com.booking.platform.graphql_gateway.service.AuthService;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartResolverTest {

    @Mock private CartClient cartClient;
    @Mock private EventClient eventClient;
    @Mock private AuthService authService;

    @InjectMocks private CartResolver resolver;

    @BeforeEach
    void auth() {
        lenient().when(authService.getAuthenticatedUserId()).thenReturn("u-1");
    }

    private CartResponse oneItemCart() {
        return CartResponse.newBuilder()
                .addItems(com.booking.platform.common.grpc.booking.CartItem.newBuilder()
                        .setId("item-1").setEventId("ev-1").setEventTitle("Rock Fest")
                        .setSeatCategory("VIP").setQuantity(2).setUnitPrice("50.00").setCurrency("USD")
                        .build())
                .build();
    }

    @Test
    void cart_mapsResponseAndDerivesTotal() {
        when(cartClient.getCart()).thenReturn(oneItemCart());

        Cart cart = resolver.cart();

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.currency()).isEqualTo("USD");
        assertThat(cart.totalPrice()).isEqualTo("100.00");   // 50.00 × 2
    }

    @Test
    void addToCart_delegatesToClient() {
        AddToCartInput input = new AddToCartInput("ev-1", "Rock Fest", "VIP", 2, "50.00", "USD");
        when(cartClient.addToCart(input)).thenReturn(oneItemCart());

        Cart cart = resolver.addToCart(input);

        verify(cartClient).addToCart(input);
        assertThat(cart.items()).hasSize(1);
    }

    @Test
    void updateCartItem_delegatesToClient() {
        when(cartClient.updateCartItem("item-1", 3)).thenReturn(oneItemCart());

        resolver.updateCartItem("item-1", 3);

        verify(cartClient).updateCartItem("item-1", 3);
    }

    @Test
    void removeFromCart_delegatesToClient() {
        when(cartClient.removeFromCart("item-1")).thenReturn(CartResponse.getDefaultInstance());

        Cart cart = resolver.removeFromCart("item-1");

        verify(cartClient).removeFromCart("item-1");
        assertThat(cart.items()).isEmpty();
        assertThat(cart.currency()).isNull();
    }

    @Test
    void clearCart_delegatesToClient() {
        when(cartClient.clearCart()).thenReturn(CartResponse.getDefaultInstance());

        resolver.clearCart();

        verify(cartClient).clearCart();
    }

    @Test
    void event_hydratesFromEventService() {
        CartItem item = new CartItem("item-1", "ev-1", "Rock Fest", "VIP", 2, "50.00", "USD");
        when(eventClient.getEvent("ev-1")).thenReturn(EventResponse.newBuilder()
                .setEvent(EventInfo.newBuilder().setId("ev-1").setTitle("Rock Fest").build())
                .build());

        Event event = resolver.event(item);

        assertThat(event).isNotNull();
        assertThat(event.id()).isEqualTo("ev-1");
    }

    @Test
    void event_whenEventGone_returnsNull() {
        CartItem item = new CartItem("item-1", "ev-gone", "Rock Fest", "VIP", 2, "50.00", "USD");
        when(eventClient.getEvent("ev-gone")).thenThrow(Status.NOT_FOUND.asRuntimeException());

        assertThat(resolver.event(item)).isNull();
    }
}
