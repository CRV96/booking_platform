package com.booking.platform.analytics_service.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis cache configuration for analytics-service.
 *
 * <p>Cache names and TTLs (default 5 minutes, configurable via {@code analytics.cache.ttl-minutes}):
 * <ul>
 *   <li>{@code analytics:event-stats} — per-event stats</li>
 *   <li>{@code analytics:daily-metrics} — daily platform metrics</li>
 *   <li>{@code analytics:category-stats} — per-category stats</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_EVENT_STATS    = "analytics:event-stats";
    public static final String CACHE_DAILY_METRICS  = "analytics:daily-metrics";
    public static final String CACHE_CATEGORY_STATS = "analytics:category-stats";

    @Value("${analytics.cache.ttl-minutes:5}")
    private long cacheTtlMinutes;

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(mapper)))
                .disableCachingNullValues()
                .entryTtl(Duration.ofMinutes(cacheTtlMinutes));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .build();
    }
}
