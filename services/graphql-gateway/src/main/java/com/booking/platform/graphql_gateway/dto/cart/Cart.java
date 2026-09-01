package com.booking.platform.graphql_gateway.dto.cart;

import com.booking.platform.common.grpc.booking.CartResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * GraphQL DTO for the whole cart. Maps from the gRPC {@code CartResponse} and derives
 * the total from the line snapshots.
 */
public record Cart(
        List<CartItem> items,
        String totalPrice,
        String currency
) {
    public static Cart fromGrpc(CartResponse response) {
        List<CartItem> items = response.getItemsList().stream()
                .map(CartItem::fromGrpc)
                .toList();

        BigDecimal total = items.stream()
                .map(i -> new BigDecimal(i.unitPrice()).multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        String currency = items.isEmpty() ? null : items.get(0).currency();

        return new Cart(items, total.toPlainString(), currency);
    }
}
