package com.booking.platform.booking_service.grpc;

import com.booking.platform.booking_service.dto.AddCartItemDto;
import com.booking.platform.booking_service.entity.CartItemEntity;
import com.booking.platform.booking_service.grpc.service.CartGrpcService;
import com.booking.platform.booking_service.service.CartService;
import com.booking.platform.booking_service.validation.CartValidation;
import com.booking.platform.common.grpc.booking.*;
import com.booking.platform.common.grpc.context.GrpcUserContext;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartGrpcServiceTest {

    @Mock private CartService cartService;
    @Mock private CartValidation cartValidation;
    @Mock private StreamObserver<CartResponse> observer;

    @InjectMocks private CartGrpcService grpcService;

    private static final String USER_ID = "user-1";
    private static final UUID ITEM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private Context.CancellableContext grpcCtx;
    private Context previousContext;

    @BeforeEach
    void attachUserContext() {
        grpcCtx = Context.current().withValue(GrpcUserContext.USER_ID, USER_ID).withCancellation();
        previousContext = grpcCtx.attach();
        when(cartService.getCart(USER_ID)).thenReturn(List.of(cartEntity()));
    }

    @AfterEach
    void detachContext() {
        grpcCtx.detach(previousContext);
        grpcCtx.cancel(null);
    }

    private CartItemEntity cartEntity() {
        return CartItemEntity.builder()
                .id(ITEM_ID).userId(USER_ID).eventId("ev-1").eventTitle("Rock Fest")
                .seatCategory("VIP").quantity(2).unitPrice(new BigDecimal("50.00")).currency("USD")
                .build();
    }

    private AddToCartRequest.Builder validAdd() {
        return AddToCartRequest.newBuilder()
                .setEventId("ev-1").setEventTitle("Rock Fest").setSeatCategory("VIP")
                .setQuantity(2).setUnitPrice("50.00").setCurrency("USD");
    }

    private void detachUser() {
        grpcCtx.detach(previousContext);
        grpcCtx.cancel(null);
        previousContext = Context.current().attach();
    }

    @Test
    void getCart_mapsEntitiesToResponse() {
        grpcService.getCart(GetCartRequest.getDefaultInstance(), observer);

        ArgumentCaptor<CartResponse> captor = ArgumentCaptor.forClass(CartResponse.class);
        verify(observer).onNext(captor.capture());
        assertThat(captor.getValue().getItemsCount()).isEqualTo(1);
        assertThat(captor.getValue().getItems(0).getId()).isEqualTo(ITEM_ID.toString());
        assertThat(captor.getValue().getItems(0).getUnitPrice()).isEqualTo("50.00");
        verify(observer).onCompleted();
    }

    @Test
    void getCart_noUser_throws() {
        detachUser();
        assertThatThrownBy(() -> grpcService.getCart(GetCartRequest.getDefaultInstance(), observer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addToCart_valid_validatesAndDelegates() {
        grpcService.addToCart(validAdd().build(), observer);

        ArgumentCaptor<AddCartItemDto> captor = ArgumentCaptor.forClass(AddCartItemDto.class);
        verify(cartValidation).validate(captor.capture());
        verify(cartService).addItem(captor.getValue());
        AddCartItemDto dto = captor.getValue();
        assertThat(dto.userId()).isEqualTo(USER_ID);
        assertThat(dto.eventId()).isEqualTo("ev-1");
        assertThat(dto.unitPrice()).isEqualByComparingTo("50.00");
        verify(observer).onCompleted();
    }

    @Test
    void addToCart_invalidUnitPrice_throwsBeforeDelegating() {
        assertThatThrownBy(() -> grpcService.addToCart(validAdd().setUnitPrice("abc").build(), observer))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unit_price");
        verify(cartService, never()).addItem(any());
    }

    @Test
    void updateCartItem_valid_validatesQuantityAndDelegates() {
        grpcService.updateCartItem(
                UpdateCartItemRequest.newBuilder().setCartItemId(ITEM_ID.toString()).setQuantity(4).build(),
                observer);

        verify(cartValidation).validateQuantity(4);
        verify(cartService).updateItemQuantity(USER_ID, ITEM_ID, 4);
        verify(observer).onCompleted();
    }

    @Test
    void updateCartItem_invalidUuid_throws() {
        assertThatThrownBy(() -> grpcService.updateCartItem(
                UpdateCartItemRequest.newBuilder().setCartItemId("bad").setQuantity(1).build(), observer))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cart_item_id");
    }

    @Test
    void removeFromCart_valid_delegates() {
        grpcService.removeFromCart(
                RemoveFromCartRequest.newBuilder().setCartItemId(ITEM_ID.toString()).build(), observer);

        verify(cartService).removeItem(USER_ID, ITEM_ID);
        verify(observer).onCompleted();
    }

    @Test
    void clearCart_delegates() {
        grpcService.clearCart(ClearCartRequest.getDefaultInstance(), observer);

        verify(cartService).clearCart(USER_ID);
        verify(observer).onCompleted();
    }
}
