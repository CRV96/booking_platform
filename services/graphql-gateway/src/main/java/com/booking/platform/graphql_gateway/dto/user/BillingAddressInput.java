package com.booking.platform.graphql_gateway.dto.user;

/**
 * GraphQL input for a structured billing address.
 */
public record BillingAddressInput(
    String fullName,
    String line1,
    String line2,
    String city,
    String state,
    String postalCode,
    String country
) {}
