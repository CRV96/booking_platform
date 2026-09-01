package com.booking.platform.graphql_gateway.dto.cart;

/**
 * GraphQL DTO for one cart line. Maps from the gRPC {@code CartItem}. The live
 * {@code event} field is resolved separately by the cart resolver (hydration).
 */
public record CartItem(
        String id,
        String eventId,
        String eventTitle,
        String seatCategory,
        int quantity,
        String unitPrice,
        String currency
) {
    public static CartItem fromGrpc(com.booking.platform.common.grpc.booking.CartItem item) {
        return new CartItem(
                item.getId(),
                item.getEventId(),
                item.getEventTitle(),
                item.getSeatCategory(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getCurrency()
        );
    }
}
