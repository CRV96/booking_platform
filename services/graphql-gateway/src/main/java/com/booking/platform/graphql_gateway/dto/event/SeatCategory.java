package com.booking.platform.graphql_gateway.dto.event;

/**
 * GraphQL DTO representing a seat category embedded in an event.
 */
public record SeatCategory(
        String name,
        String price,
        String currency,
        int totalSeats,
        int availableSeats
) {
    public static SeatCategory fromGrpc(com.booking.platform.common.grpc.event.SeatCategoryInfo seatCategoryInfo) {
        return new SeatCategory(
                seatCategoryInfo.getName(),
                String.valueOf(seatCategoryInfo.getPrice()),
                seatCategoryInfo.getCurrency(),
                seatCategoryInfo.getTotalSeats(),
                seatCategoryInfo.getAvailableSeats()
        );
    }
}
