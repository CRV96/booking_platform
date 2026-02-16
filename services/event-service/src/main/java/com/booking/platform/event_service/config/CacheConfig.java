package com.booking.platform.event_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;
import java.util.Map;

/**
 * Redis cache configuration for the event-service.
 *
 * <p>Cache names and TTLs:
 * <ul>
 *   <li>{@code event:detail} — single event by ID — 5 minutes</li>
 *   <li>{@code events:featured} — top upcoming published events — 2 minutes</li>
 *   <li>{@code events:search} — search results keyed by filter hash — 1 minute</li>
 * </ul>
 *
 * <p>Values are serialized as JSON so they are human-readable in Redis and
 * survive service restarts without deserialization errors from binary formats.
 */
@Configuration
@EnableCaching
@EnableScheduling
public class CacheConfig {

    public static final String CACHE_EVENT_DETAIL   = "event:detail";
    public static final String CACHE_EVENTS_FEATURED = "events:featured";
    public static final String CACHE_EVENTS_SEARCH   = "events:search";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);

        // Base configuration applied to all caches unless overridden
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(mapper)))
                .disableCachingNullValues();

        // Per-cache TTL overrides
        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
                CACHE_EVENT_DETAIL,    defaultConfig.entryTtl(Duration.ofMinutes(5)),
                CACHE_EVENTS_FEATURED, defaultConfig.entryTtl(Duration.ofMinutes(2)),
                CACHE_EVENTS_SEARCH,   defaultConfig.entryTtl(Duration.ofMinutes(1))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
