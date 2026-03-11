package com.booking.platform.graphql_gateway.dto;

public record RateLimitResult(boolean allowed, long currentCount, int limit, long retryAfterSeconds) {

    public long remaining() {
        return Math.max(0, limit - currentCount);
    }
}
