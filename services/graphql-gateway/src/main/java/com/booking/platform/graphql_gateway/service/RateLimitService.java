package com.booking.platform.graphql_gateway.service;

import com.booking.platform.graphql_gateway.dto.RateLimitResult;

/**
 * Service for checking rate limits against a client identifier.
 */
public interface RateLimitService {

    /**
     * Checks whether the given key has exceeded its rate limit.
     *
     * @param key           identifier (e.g. "ip:192.168.1.1" or "user:abc-123")
     * @param maxRequests   maximum allowed requests in the window
     * @param windowSeconds window duration in seconds
     * @return result indicating whether the request is allowed
     */
    RateLimitResult checkLimit(String key, int maxRequests, int windowSeconds);
}
