package com.booking.platform.common.security;

import com.booking.platform.common.logging.ApplicationLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Service for managing JWT token blacklist in Redis.
 *
 * When a user logs out or a token needs to be revoked, the token's
 * unique identifier (jti claim) is added to the blacklist with a TTL
 * matching the token's remaining lifetime.
 *
 * Redis automatically removes expired entries, so no cleanup is needed.
 *
 * Redis key format: jwt:blacklist:{jti}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;

    /**
     * Adds a token to the blacklist.
     *
     * @param jti    the JWT ID (unique token identifier)
     * @param expiry the token's expiration time
     */
    public void blacklist(String jti, Instant expiry) {
        if (jti == null || jti.isBlank()) {
            ApplicationLogger.logMessage(log, Level.WARN, "Cannot blacklist token: missing jti claim");
            return;
        }

        long ttlSeconds = Duration.between(Instant.now(), expiry).getSeconds();

        if (ttlSeconds <= 0) {
            ApplicationLogger.logMessage(log, Level.DEBUG, "Token already expired, no need to blacklist: {}", jti);
            return;
        }

        String key = BLACKLIST_PREFIX + jti;
        redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(ttlSeconds));
        ApplicationLogger.logMessage(log, Level.INFO, "Token blacklisted: {} (TTL: {}s)", jti, ttlSeconds);
    }

    /**
     * Checks if a token is blacklisted.
     *
     * @param jti the JWT ID to check
     * @return true if the token is blacklisted, false otherwise
     */
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }

        String key = BLACKLIST_PREFIX + jti;
        Boolean exists = redisTemplate.hasKey(key);

        if (Boolean.TRUE.equals(exists)) {
            ApplicationLogger.logMessage(log, Level.DEBUG, "Token is blacklisted: {}", jti);
            return true;
        }

        return false;
    }
}
