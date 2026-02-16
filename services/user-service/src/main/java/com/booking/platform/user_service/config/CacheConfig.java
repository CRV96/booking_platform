package com.booking.platform.user_service.config;

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

@Configuration
@EnableCaching
@EnableScheduling
public class CacheConfig {

    public static final String CACHE_USER_BY_ID   = "user:profile";
    public static final String CACHE_USER_BY_EMAIL = "user:email";
    public static final String CACHE_USER_BY_USERNAME = "user:username";

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
                CACHE_USER_BY_ID,    defaultConfig.entryTtl(Duration.ofMinutes(10)),
                CACHE_USER_BY_EMAIL, defaultConfig.entryTtl(Duration.ofMinutes(10)),
                CACHE_USER_BY_USERNAME, defaultConfig.entryTtl(Duration.ofMinutes(10))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
