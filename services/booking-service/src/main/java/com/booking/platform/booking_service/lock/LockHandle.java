package com.booking.platform.booking_service.lock;

/**
 * Represents an acquired distributed lock.
 * Holds the Redis key and the owner value needed for safe release via Lua script.
 */
public record LockHandle(String lockKey, String lockValue) {
}
