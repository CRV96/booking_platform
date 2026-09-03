package com.booking.platform.graphql_gateway.dto.user;

/**
 * GraphQL DTO for a structured billing address (output type).
 */
public record BillingAddress(
    String fullName,
    String line1,
    String line2,
    String city,
    String state,
    String postalCode,
    String country
) {
    public static BillingAddress fromGrpc(com.booking.platform.common.grpc.user.BillingAddress a) {
        return new BillingAddress(
            a.getFullName(),
            a.getLine1(),
            a.getLine2(),
            a.getCity(),
            a.getState(),
            a.getPostalCode(),
            a.getCountry()
        );
    }
}
