package com.booking.platform.graphql_gateway.dto;

import java.util.List;

public record UserConnection(
    List<User> users,
    int totalCount,
    int page,
    int pageSize,
    int totalPages
) {}
