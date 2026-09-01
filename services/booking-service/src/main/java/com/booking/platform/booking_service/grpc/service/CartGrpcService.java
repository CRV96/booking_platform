package com.booking.platform.booking_service.grpc.service;

import com.booking.platform.booking_service.constants.FieldConst;
import com.booking.platform.booking_service.dto.AddCartItemDto;
import com.booking.platform.booking_service.entity.CartItemEntity;
import com.booking.platform.booking_service.service.CartService;
import com.booking.platform.booking_service.util.GrpcRequestUtils;
import com.booking.platform.booking_service.validation.CartValidation;
import com.booking.platform.common.grpc.booking.*;
import com.booking.platform.common.logging.ApplicationLogger;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.event.Level;

import java.util.List;

/**
 * gRPC endpoint for the per-user cart. Maps the request to a command, validates it,
 * and delegates to {@link CartService}.
 *
 * <p>The user ID is taken from the JWT via {@link GrpcRequestUtils#requireUserId()} —
 * never from the request. Every mutation responds with the full, freshly-read cart so
 * the caller ({@code graphql-gateway}) always has current state. Exceptions are mapped
 * to gRPC status codes by the shared {@code GrpcExceptionInterceptor}.</p>
 */
@GrpcService
@Slf4j
@RequiredArgsConstructor
public class CartGrpcService extends CartServiceGrpc.CartServiceImplBase {

    private final CartService cartService;
    private final CartValidation cartValidation;

    @Override
    public void getCart(GetCartRequest request, StreamObserver<CartResponse> responseObserver) {
        String userId = GrpcRequestUtils.requireUserId();
        ApplicationLogger.logMessage(log, Level.DEBUG, "gRPC GetCart: user='{}'", userId);

        responseObserver.onNext(currentCart(userId));
        responseObserver.onCompleted();
    }

    @Override
    public void addToCart(AddToCartRequest request, StreamObserver<CartResponse> responseObserver) {
        String userId = GrpcRequestUtils.requireUserId();

        AddCartItemDto dto = AddCartItemDto.builder()
                .userId(userId)
                .eventId(request.getEventId())
                .eventTitle(request.getEventTitle())
                .seatCategory(request.getSeatCategory())
                .quantity(request.getQuantity())
                .unitPrice(GrpcRequestUtils.parseDecimal(request.getUnitPrice(), FieldConst.UNIT_PRICE))
                .currency(request.getCurrency())
                .build();
        cartValidation.validate(dto);

        ApplicationLogger.logMessage(log, Level.DEBUG,
                "gRPC AddToCart: user='{}', event='{}', category='{}', qty={}",
                userId, dto.eventId(), dto.seatCategory(), dto.quantity());

        cartService.addItem(dto);

        responseObserver.onNext(currentCart(userId));
        responseObserver.onCompleted();
    }

    @Override
    public void updateCartItem(UpdateCartItemRequest request, StreamObserver<CartResponse> responseObserver) {
        String userId = GrpcRequestUtils.requireUserId();
        cartValidation.validateQuantity(request.getQuantity());

        ApplicationLogger.logMessage(log, Level.DEBUG,
                "gRPC UpdateCartItem: user='{}', item='{}', qty={}",
                userId, request.getCartItemId(), request.getQuantity());

        cartService.updateItemQuantity(userId,
                GrpcRequestUtils.parseUuid(request.getCartItemId(), FieldConst.CART_ITEM_ID), request.getQuantity());

        responseObserver.onNext(currentCart(userId));
        responseObserver.onCompleted();
    }

    @Override
    public void removeFromCart(RemoveFromCartRequest request, StreamObserver<CartResponse> responseObserver) {
        String userId = GrpcRequestUtils.requireUserId();

        ApplicationLogger.logMessage(log, Level.DEBUG,
                "gRPC RemoveFromCart: user='{}', item='{}'", userId, request.getCartItemId());

        cartService.removeItem(userId, GrpcRequestUtils.parseUuid(request.getCartItemId(), FieldConst.CART_ITEM_ID));

        responseObserver.onNext(currentCart(userId));
        responseObserver.onCompleted();
    }

    @Override
    public void clearCart(ClearCartRequest request, StreamObserver<CartResponse> responseObserver) {
        String userId = GrpcRequestUtils.requireUserId();

        ApplicationLogger.logMessage(log, Level.DEBUG, "gRPC ClearCart: user='{}'", userId);

        cartService.clearCart(userId);

        responseObserver.onNext(currentCart(userId));
        responseObserver.onCompleted();
    }

    /** Reads the user's cart and maps it to the gRPC response. */
    private CartResponse currentCart(String userId) {
        List<CartItem> items = cartService.getCart(userId).stream()
                .map(this::toProto)
                .toList();
        ApplicationLogger.logMessage(log, Level.DEBUG, "Current cart for user='{}': {} items", userId, items.size());

        return CartResponse.newBuilder().addAllItems(items).build();
    }

    private CartItem toProto(CartItemEntity e) {
        return CartItem.newBuilder()
                .setId(e.getId().toString())
                .setEventId(e.getEventId())
                .setEventTitle(e.getEventTitle())
                .setSeatCategory(e.getSeatCategory())
                .setQuantity(e.getQuantity())
                .setUnitPrice(e.getUnitPrice().toPlainString())
                .setCurrency(e.getCurrency())
                .build();
    }
}
